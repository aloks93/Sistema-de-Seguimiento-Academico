package py.edu.upe.hernandarias.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.dto.CalificacionRequest;
import py.edu.upe.hernandarias.dto.CalificacionResponse;
import py.edu.upe.hernandarias.dto.CalificacionUpdateRequest;
import py.edu.upe.hernandarias.entity.Calificacion;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;

@Service
@RequiredArgsConstructor
public class CalificacionService {

    private final CalificacionRepository calificacionRepository;
    private final EstudianteRepository estudianteRepository;
    private final MateriaRepository materiaRepository;

    /** Calificaciones del estudiante vinculado al usuario autenticado. */
    @Transactional(readOnly = true)
    public List<CalificacionResponse> listarPorUsuario(String username) {
        Estudiante estudiante = estudianteRepository.findByUsuarioUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante para el usuario", username));

        return calificacionRepository
                .findByEstudianteIdOrderByMateriaSemestreAscMateriaNombreAsc(estudiante.getId())
                .stream()
                .map(CalificacionResponse::from)
                .toList();
    }

    @Transactional
    public CalificacionResponse registrar(CalificacionRequest request) {
        Estudiante estudiante = estudianteRepository.findById(request.estudianteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", request.estudianteId()));
        Materia materia = materiaRepository.findById(request.materiaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Materia", request.materiaId()));

        if (calificacionRepository.existsByEstudianteIdAndMateriaId(estudiante.getId(), materia.getId())) {
            throw new ConflictoException(
                    "El estudiante %s ya tiene una calificación en %s; use PATCH para modificarla"
                            .formatted(estudiante.getMatricula(), materia.getCodigo()));
        }

        Calificacion calificacion = Calificacion.builder()
                .estudiante(estudiante)
                .materia(materia)
                .nota(request.nota())
                .build();

        return CalificacionResponse.from(calificacionRepository.saveAndFlush(calificacion));
    }

    @Transactional
    public CalificacionResponse actualizar(Long id, CalificacionUpdateRequest request) {
        Calificacion calificacion = calificacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Calificación", id));

        calificacion.setNota(request.nota());

        // flush para que @UpdateTimestamp se refleje en la respuesta
        return CalificacionResponse.from(calificacionRepository.saveAndFlush(calificacion));
    }
}
