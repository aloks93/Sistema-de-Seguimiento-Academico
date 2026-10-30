package py.edu.upe.hernandarias.service;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import py.edu.upe.hernandarias.dto.EstudianteRequest;
import py.edu.upe.hernandarias.dto.EstudianteResponse;
import py.edu.upe.hernandarias.dto.EstudianteUpdateRequest;
import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class EstudianteService {

    private final EstudianteRepository estudianteRepository;
    private final CarreraRepository carreraRepository;
    private final UsuarioRepository usuarioRepository;
    private final CalificacionRepository calificacionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermisosService permisosService;

    /**
     * Estudiantes (opcionalmente de una carrera). Un docente solo ve los de las carreras
     * donde tiene materias a cargo.
     */
    @Transactional(readOnly = true)
    public List<EstudianteResponse> listar(Long carreraId, String usernameActual) {
        List<Estudiante> estudiantes = carreraId == null
                ? estudianteRepository.findAllByOrderByNombreCompletoAsc()
                : estudianteRepository.findByCarreraIdOrderByNombreCompletoAsc(carreraId);
        if (!permisosService.esAdmin(usernameActual)) {
            Set<Long> propias = permisosService.carrerasDelDocente(usernameActual);
            estudiantes = estudiantes.stream()
                    .filter(estudiante -> propias.contains(estudiante.getCarrera().getId()))
                    .toList();
        }
        return estudiantes.stream().map(EstudianteResponse::from).toList();
    }

    /**
     * Crea el estudiante y su usuario con rol ESTUDIANTE para que pueda consultar sus notas.
     * Un docente solo puede crearlo en una carrera donde tiene materias a cargo.
     */
    @Transactional
    public EstudianteResponse crear(EstudianteRequest request, String usernameActual) {
        permisosService.verificarCarrera(request.carreraId(), usernameActual);
        String username = request.username().trim();
        String matricula = request.matricula().trim();
        if (usuarioRepository.existsByUsername(username)) {
            throw new ConflictoException("Ya existe un usuario con el nombre " + username);
        }
        if (estudianteRepository.existsByMatricula(matricula)) {
            throw new ConflictoException("Ya existe un estudiante con la matrícula " + matricula);
        }

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(Rol.ESTUDIANTE)
                .build());

        Estudiante estudiante = estudianteRepository.save(Estudiante.builder()
                .usuario(usuario)
                .matricula(matricula)
                .nombreCompleto(request.nombreCompleto().trim())
                .carrera(buscarCarrera(request.carreraId()))
                .build());

        return EstudianteResponse.from(estudiante);
    }

    /** Un docente solo edita estudiantes de sus carreras y no puede pasarlos a otra ajena. */
    @Transactional
    public EstudianteResponse actualizar(Long id, EstudianteUpdateRequest request, String usernameActual) {
        Estudiante estudiante = buscar(id);
        permisosService.verificarCarrera(estudiante.getCarrera().getId(), usernameActual);
        permisosService.verificarCarrera(request.carreraId(), usernameActual);
        String matricula = request.matricula().trim();
        if (estudianteRepository.existsByMatriculaAndIdNot(matricula, id)) {
            throw new ConflictoException("Ya existe otro estudiante con la matrícula " + matricula);
        }
        Carrera carrera = buscarCarrera(request.carreraId());
        if (!carrera.getId().equals(estudiante.getCarrera().getId()) && calificacionRepository.existsByEstudianteId(id)) {
            throw new ConflictoException(
                    "El estudiante %s ya tiene notas; no se puede cambiar de carrera".formatted(estudiante.getMatricula()));
        }

        estudiante.setMatricula(matricula);
        estudiante.setNombreCompleto(request.nombreCompleto().trim());
        estudiante.setCarrera(carrera);
        if (StringUtils.hasText(request.password())) {
            estudiante.getUsuario().setPasswordHash(passwordEncoder.encode(request.password()));
        }

        return EstudianteResponse.from(estudianteRepository.save(estudiante));
    }

    /** Elimina el estudiante y su usuario; solo si no tiene notas cargadas. */
    @Transactional
    public void eliminar(Long id) {
        Estudiante estudiante = buscar(id);
        if (calificacionRepository.existsByEstudianteId(id)) {
            throw new ConflictoException(
                    "El estudiante %s tiene notas cargadas; elimine primero esas notas o desactive su usuario"
                            .formatted(estudiante.getMatricula()));
        }
        Usuario usuario = estudiante.getUsuario();
        estudianteRepository.delete(estudiante);
        usuarioRepository.delete(usuario);
    }

    private Estudiante buscar(Long id) {
        return estudianteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", id));
    }

    private Carrera buscarCarrera(Long id) {
        return carreraRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Carrera", id));
    }
}
