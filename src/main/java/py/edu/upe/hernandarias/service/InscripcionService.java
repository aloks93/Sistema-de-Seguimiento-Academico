package py.edu.upe.hernandarias.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.dto.InscripcionRequest;
import py.edu.upe.hernandarias.dto.InscripcionResponse;
import py.edu.upe.hernandarias.dto.ResultadoInscripcion;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Inscripcion;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.AsistenciaRepository;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.ExoneracionRepository;
import py.edu.upe.hernandarias.repository.InscripcionRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;

/**
 * Inscripción de estudiantes a cada materia. La gestiona un ADMIN o el docente a cargo de la materia;
 * solo se inscriben estudiantes de la carrera de la materia.
 */
@Service
@RequiredArgsConstructor
public class InscripcionService {

    private final InscripcionRepository inscripcionRepository;
    private final MateriaRepository materiaRepository;
    private final EstudianteRepository estudianteRepository;
    private final CalificacionRepository calificacionRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final ExoneracionRepository exoneracionRepository;
    private final PermisosService permisosService;

    @Transactional(readOnly = true)
    public List<InscripcionResponse> listar(Long materiaId, String username) {
        Materia materia = buscarMateria(materiaId);
        permisosService.verificarMateria(materia, username);

        Set<Long> conRegistros = new LinkedHashSet<>(calificacionRepository.findEstudianteIdsByMateriaId(materiaId));
        asistenciaRepository.findByClaseMateriaId(materiaId)
                .forEach(asistencia -> conRegistros.add(asistencia.getEstudiante().getId()));

        return inscripcionRepository.findByMateriaIdOrderByEstudianteNombreCompletoAsc(materiaId).stream()
                .map(inscripcion -> {
                    Estudiante estudiante = inscripcion.getEstudiante();
                    return new InscripcionResponse(inscripcion.getId(), estudiante.getId(), estudiante.getMatricula(),
                            estudiante.getNombreCompleto(), estudiante.getUsuario().getUsername(), materiaId,
                            inscripcion.getFechaRegistro(), conRegistros.contains(estudiante.getId()));
                })
                .toList();
    }

    @Transactional
    public ResultadoInscripcion inscribir(InscripcionRequest request, String username) {
        Materia materia = buscarMateria(request.materiaId());
        permisosService.verificarMateria(materia, username);

        List<Estudiante> estudiantes = new LinkedHashSet<>(request.estudianteIds()).stream()
                .map(id -> estudianteRepository.findById(id)
                        .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", id)))
                .toList();
        for (Estudiante estudiante : estudiantes) {
            if (!estudiante.getCarrera().getId().equals(materia.getCarrera().getId())) {
                throw new ConflictoException("%s (%s) no es de la carrera %s".formatted(estudiante.getNombreCompleto(),
                        estudiante.getMatricula(), materia.getCarrera().getNombre()));
            }
        }
        return inscribirTodos(materia, estudiantes);
    }

    /** Inscribe a todos los estudiantes activos de la carrera que todavía no cursan la materia. */
    @Transactional
    public ResultadoInscripcion inscribirCarreraCompleta(Long materiaId, String username) {
        Materia materia = buscarMateria(materiaId);
        permisosService.verificarMateria(materia, username);
        List<Estudiante> estudiantes = estudianteRepository
                .findByCarreraIdOrderByNombreCompletoAsc(materia.getCarrera().getId()).stream()
                .filter(estudiante -> estudiante.getUsuario().isActivo())
                .toList();
        return inscribirTodos(materia, estudiantes);
    }

    /** Quita la inscripción; solo si el estudiante todavía no tiene notas ni asistencia en la materia. */
    @Transactional
    public void eliminar(Long id, String username) {
        Inscripcion inscripcion = inscripcionRepository.findWithMateriaById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inscripción", id));
        Materia materia = inscripcion.getMateria();
        permisosService.verificarMateria(materia, username);

        Estudiante estudiante = inscripcion.getEstudiante();
        if (calificacionRepository.existsByEstudianteIdAndMateriaId(estudiante.getId(), materia.getId())
                || asistenciaRepository.existsByEstudianteIdAndClaseMateriaId(estudiante.getId(), materia.getId())) {
            throw new ConflictoException("%s ya tiene notas o asistencia en %s; no se puede quitar la inscripción"
                    .formatted(estudiante.getNombreCompleto(), materia.getCodigo()));
        }
        exoneracionRepository.findByEstudianteIdAndMateriaId(estudiante.getId(), materia.getId())
                .ifPresent(exoneracionRepository::delete);
        inscripcionRepository.delete(inscripcion);
    }

    private ResultadoInscripcion inscribirTodos(Materia materia, Collection<Estudiante> estudiantes) {
        Set<Long> yaInscriptos = inscripcionRepository.findByMateriaIdOrderByEstudianteNombreCompletoAsc(materia.getId())
                .stream()
                .map(inscripcion -> inscripcion.getEstudiante().getId())
                .collect(Collectors.toSet());
        List<Inscripcion> nuevas = estudiantes.stream()
                .filter(estudiante -> !yaInscriptos.contains(estudiante.getId()))
                .map(estudiante -> Inscripcion.builder().estudiante(estudiante).materia(materia).build())
                .toList();
        inscripcionRepository.saveAll(nuevas);
        return new ResultadoInscripcion(nuevas.size(), estudiantes.size() - nuevas.size());
    }

    private Materia buscarMateria(Long id) {
        return materiaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Materia", id));
    }
}
