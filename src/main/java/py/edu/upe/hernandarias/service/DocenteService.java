package py.edu.upe.hernandarias.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import py.edu.upe.hernandarias.dto.DocenteRequest;
import py.edu.upe.hernandarias.dto.DocenteResponse;
import py.edu.upe.hernandarias.dto.DocenteUpdateRequest;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.exception.ConDatosAsociadosException;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class DocenteService {

    private final DocenteRepository docenteRepository;
    private final UsuarioRepository usuarioRepository;
    private final MateriaRepository materiaRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<DocenteResponse> listar() {
        Map<Long, List<String>> materiasPorDocente = materiaRepository.findByDocenteIsNotNullOrderByCodigoAsc().stream()
                .collect(groupingBy(materia -> materia.getDocente().getId(), mapping(Materia::getCodigo, toList())));

        return docenteRepository.findAllByOrderByNombreCompletoAsc().stream()
                .map(docente -> DocenteResponse.from(docente,
                        materiasPorDocente.getOrDefault(docente.getId(), List.of())))
                .toList();
    }

    /** Crea el docente y su usuario con rol DOCENTE. */
    @Transactional
    public DocenteResponse crear(DocenteRequest request) {
        String username = request.username().trim();
        if (usuarioRepository.existsByUsername(username)) {
            throw new ConflictoException("Ya existe un usuario con el nombre " + username);
        }

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(Rol.DOCENTE)
                .build());

        Docente docente = docenteRepository.save(Docente.builder()
                .usuario(usuario)
                .nombreCompleto(request.nombreCompleto().trim())
                .build());

        return DocenteResponse.from(docente, List.of());
    }

    @Transactional
    public DocenteResponse actualizar(Long id, DocenteUpdateRequest request) {
        Docente docente = buscar(id);
        docente.setNombreCompleto(request.nombreCompleto().trim());
        if (StringUtils.hasText(request.password())) {
            docente.getUsuario().setPasswordHash(passwordEncoder.encode(request.password()));
        }
        docenteRepository.save(docente);

        List<String> materias = materiaRepository.findByDocenteIsNotNullOrderByCodigoAsc().stream()
                .filter(materia -> materia.getDocente().getId().equals(id))
                .map(Materia::getCodigo)
                .toList();
        return DocenteResponse.from(docente, materias);
    }

    /**
     * Elimina el docente y su usuario. Si tiene materias a cargo solo se elimina con {@code forzar}:
     * esas materias quedan "sin asignar" (sus notas y asistencia se conservan).
     */
    @Transactional
    public void eliminar(Long id, boolean forzar) {
        Docente docente = buscar(id);
        if (materiaRepository.existsByDocenteId(id)) {
            if (!forzar) {
                throw new ConDatosAsociadosException(("%s tiene materias a cargo. Si lo elimina igual, esas materias "
                        + "quedan sin docente asignado (las notas y la asistencia se conservan).")
                        .formatted(docente.getNombreCompleto()));
            }
            materiaRepository.quitarDocente(id);
        }
        Usuario usuario = docente.getUsuario();
        docenteRepository.delete(docente);
        usuarioRepository.delete(usuario);
    }

    /** Docente cuyo usuario es {@code usuarioId}, si existe. */
    @Transactional(readOnly = true)
    public Optional<Long> idDelUsuario(Long usuarioId) {
        return docenteRepository.findByUsuarioId(usuarioId).map(Docente::getId);
    }

    private Docente buscar(Long id) {
        return docenteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Docente", id));
    }
}
