package py.edu.upe.hernandarias.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import py.edu.upe.hernandarias.dto.UsuarioRequest;
import py.edu.upe.hernandarias.dto.UsuarioResponse;
import py.edu.upe.hernandarias.dto.UsuarioUpdateRequest;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarioRepository.findAllByOrderByRolAscUsernameAsc().stream()
                .map(UsuarioResponse::from)
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
     * Activa/desactiva el usuario o cambia su contraseña.
     *
     * @param usernameActual quien hace el cambio; no puede desactivarse a sí mismo
     */
    @Transactional
    public UsuarioResponse actualizar(Long id, UsuarioUpdateRequest request, String usernameActual) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", id));

        if (request.activo() != null) {
            if (!request.activo() && usuario.getUsername().equals(usernameActual)) {
                throw new ConflictoException("No puede desactivar su propio usuario");
            }
            usuario.setActivo(request.activo());
        }
        if (StringUtils.hasText(request.password())) {
            usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        return UsuarioResponse.from(usuarioRepository.save(usuario));
    }
}
