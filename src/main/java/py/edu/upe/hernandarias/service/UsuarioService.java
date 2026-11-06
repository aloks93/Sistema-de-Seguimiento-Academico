package py.edu.upe.hernandarias.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import py.edu.upe.hernandarias.dto.UsuarioRequest;
import py.edu.upe.hernandarias.dto.UsuarioResponse;
import py.edu.upe.hernandarias.dto.UsuarioUpdateRequest;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.OperacionNoPermitidaException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

/**
 * Cuentas de acceso (solo ADMIN). Jerarquía entre administradores: cualquier ADMIN crea otros
 * administradores, pero solo el super administrador puede eliminarlos, desactivarlos, cambiarles la
 * contraseña o quitarles la verificación en dos pasos. Al super administrador nadie más lo modifica.
 */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final DocenteRepository docenteRepository;
    private final EstudianteRepository estudianteRepository;
    private final DocenteService docenteService;
    private final EstudianteService estudianteService;
    private final PasswordEncoder passwordEncoder;

    /** Todas las cuentas con el nombre de la persona (y la matrícula y carrera de los estudiantes). */
    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        Map<Long, Docente> docentes = docenteRepository.findAllByOrderByNombreCompletoAsc().stream()
                .collect(Collectors.toMap(d -> d.getUsuario().getId(), Function.identity()));
        Map<Long, Estudiante> estudiantes = estudianteRepository.findAllByOrderByNombreCompletoAsc().stream()
                .collect(Collectors.toMap(e -> e.getUsuario().getId(), Function.identity()));

        return usuarioRepository.findAllByOrderByRolAscUsernameAsc().stream()
                .map(usuario -> {
                    Docente docente = docentes.get(usuario.getId());
                    if (docente != null) {
                        return UsuarioResponse.from(usuario, docente.getNombreCompleto(), null);
                    }
                    Estudiante estudiante = estudiantes.get(usuario.getId());
                    if (estudiante != null) {
                        return UsuarioResponse.from(usuario, estudiante.getNombreCompleto(),
                                estudiante.getMatricula() + " · " + estudiante.getCarrera().getNombre());
                    }
                    return UsuarioResponse.from(usuario);
                })
                .toList();
    }

    /** Crea un usuario ADMIN (los docentes y estudiantes tienen su propio alta con perfil). */
    @Transactional
    public UsuarioResponse crear(UsuarioRequest request) {
        String username = request.username().trim();
        if (usuarioRepository.existsByUsername(username)) {
            throw new ConflictoException("Ya existe un usuario con el nombre " + username);
        }

        Usuario usuario = Usuario.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(Rol.ADMIN)
                .build();

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    /**
     * Activa/desactiva el usuario, cambia su contraseña o le quita la verificación en dos pasos.
     *
     * @param usernameActual quien hace el cambio; no puede desactivarse a sí mismo
     */
    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioUpdateRequest request, String usernameActual) {
        Usuario usuario = buscar(id);
        verificarPuedeGestionar(usuario, usernameActual);

        if (request.activo() != null) {
            if (!request.activo() && usuario.getUsername().equals(usernameActual)) {
                throw new ConflictoException("No puede desactivar su propio usuario");
            }
            usuario.setActivo(request.activo());
        }
        if (StringUtils.hasText(request.password())) {
            usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        // Para quien perdió el celular: vuelve a ingresar solo con contraseña y puede reactivarlo
        if (Boolean.TRUE.equals(request.quitarDosFactores())) {
            usuario.setTotpSecreto(null);
            usuario.setTotpSecretoPendiente(null);
        }

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }

    /**
     * Elimina la cuenta y, si es docente o estudiante, también su perfil. Con datos asociados (notas,
     * asistencia, materias a cargo) responde 409 salvo que se pida {@code forzar}.
     */
    @Transactional
    public void eliminar(Long id, boolean forzar, String usernameActual) {
        Usuario usuario = buscar(id);
        if (usuario.getUsername().equals(usernameActual)) {
            throw new ConflictoException("No puede eliminar su propio usuario");
        }
        verificarPuedeGestionar(usuario, usernameActual);

        switch (usuario.getRol()) {
            case DOCENTE -> docenteService.idDelUsuario(id).ifPresentOrElse(
                    docenteId -> docenteService.eliminar(docenteId, forzar),
                    () -> usuarioRepository.delete(usuario));
            case ESTUDIANTE -> estudianteService.idDelUsuario(id).ifPresentOrElse(
                    estudianteId -> estudianteService.eliminar(estudianteId, forzar),
                    () -> usuarioRepository.delete(usuario));
            case ADMIN -> usuarioRepository.delete(usuario);
        }
    }

    @Transactional(readOnly = true)
    public boolean esSuperAdmin(String username) {
        return usuarioRepository.findByUsername(username).map(Usuario::isSuperAdmin).orElse(false);
    }

    /**
     * Lanza 403 si {@code usernameActual} no puede modificar a {@code objetivo}: al super administrador
     * solo se lo modifica él mismo, y a otro administrador solo el super administrador.
     */
    @Transactional(readOnly = true)
    public void verificarPuedeGestionar(Usuario objetivo, String usernameActual) {
        if (objetivo.getUsername().equals(usernameActual)) {
            return;
        }
        if (objetivo.isSuperAdmin()) {
            throw new OperacionNoPermitidaException("El super administrador no puede ser modificado por otro usuario");
        }
        if (objetivo.getRol() == Rol.ADMIN && !esSuperAdmin(usernameActual)) {
            throw new OperacionNoPermitidaException(
                    "Solo el super administrador puede modificar o eliminar a otros administradores");
        }
    }

    Usuario buscar(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));
    }
}
