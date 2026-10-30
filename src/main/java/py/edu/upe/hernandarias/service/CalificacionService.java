package py.edu.upe.hernandarias.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.dto.CalificacionRequest;
import py.edu.upe.hernandarias.dto.CalificacionResponse;
import py.edu.upe.hernandarias.dto.CalificacionUpdateRequest;
import py.edu.upe.hernandarias.entity.Calificacion;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.OperacionNoPermitidaException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

/**
 * Reglas de las notas: un ADMIN opera sobre cualquier materia; un DOCENTE solo sobre
 * las materias que tiene a cargo. El estudiante debe ser de la carrera de la materia.
 */
@Service
@RequiredArgsConstructor
public class CalificacionService {

    private static final Sort ORDEN = Sort.by("estudiante.nombreCompleto", "materia.semestre", "materia.nombre");

    private final CalificacionRepository calificacionRepository;
    private final EstudianteRepository estudianteRepository;
    private final MateriaRepository materiaRepository;
    private final UsuarioRepository usuarioRepository;

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

    /**
     * Notas visibles para el usuario (un docente solo ve las de sus materias),
     * opcionalmente filtradas por estudiante y/o materia.
     */
    @Transactional(readOnly = true)
    public List<CalificacionResponse> listar(Long estudianteId, Long materiaId, String username) {
        Specification<Calificacion> filtro = (root, query, cb) -> cb.conjunction();
        if (estudianteId != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("estudiante").get("id"), estudianteId));
        }
        if (materiaId != null) {
            filtro = filtro.and((root, query, cb) -> cb.equal(root.get("materia").get("id"), materiaId));
        }
        if (!esAdmin(username)) {
            filtro = filtro.and((root, query, cb) -> cb.equal(
                    root.get("materia").get("docente").get("usuario").get("username"), username));
        }

        return calificacionRepository.findAll(filtro, ORDEN).stream()
                .map(CalificacionResponse::from)
                .toList();
    }

    @Transactional
    public CalificacionResponse registrar(CalificacionRequest request, String username) {
        Estudiante estudiante = estudianteRepository.findById(request.estudianteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", request.estudianteId()));
        Materia materia = materiaRepository.findById(request.materiaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Materia", request.materiaId()));
        verificarPermiso(materia, username);

        if (!estudiante.getCarrera().getId().equals(materia.getCarrera().getId())) {
            throw new ConflictoException("La materia %s no pertenece a la carrera del estudiante %s (%s)"
                    .formatted(materia.getCodigo(), estudiante.getMatricula(), estudiante.getCarrera().getNombre()));
        }
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
    public CalificacionResponse actualizar(Long id, CalificacionUpdateRequest request, String username) {
        Calificacion calificacion = buscar(id);
        verificarPermiso(calificacion.getMateria(), username);

        calificacion.setNota(request.nota());

        // flush para que @UpdateTimestamp se refleje en la respuesta
        return CalificacionResponse.from(calificacionRepository.saveAndFlush(calificacion));
    }

    @Transactional
    public void eliminar(Long id, String username) {
        Calificacion calificacion = buscar(id);
        verificarPermiso(calificacion.getMateria(), username);
        calificacionRepository.delete(calificacion);
    }

    private Calificacion buscar(Long id) {
        return calificacionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Calificación", id));
    }

    private void verificarPermiso(Materia materia, String username) {
        if (esAdmin(username)) {
            return;
        }
        Docente docente = materia.getDocente();
        if (docente == null || !docente.getUsuario().getUsername().equals(username)) {
            throw new OperacionNoPermitidaException(
                    "Solo el docente a cargo de %s puede cargar sus notas".formatted(materia.getCodigo()));
        }
    }

    private boolean esAdmin(String username) {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> usuario.getRol() == Rol.ADMIN)
                .orElse(false);
    }
}
