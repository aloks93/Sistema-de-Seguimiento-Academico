package py.edu.upe.hernandarias.repository;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import py.edu.upe.hernandarias.entity.Calificacion;

public interface CalificacionRepository extends JpaRepository<Calificacion, Long>, JpaSpecificationExecutor<Calificacion> {

    @EntityGraph(attributePaths = {"materia", "materia.docente", "estudiante"})
    List<Calificacion> findByEstudianteIdOrderByMateriaSemestreAscMateriaNombreAsc(Long estudianteId);

    /** Búsqueda con filtros opcionales (ver CalificacionService#listar). */
    @Override
    @EntityGraph(attributePaths = {"materia", "materia.docente", "estudiante"})
    List<Calificacion> findAll(Specification<Calificacion> spec, Sort sort);

    boolean existsByEstudianteIdAndMateriaId(Long estudianteId, Long materiaId);

    boolean existsByEstudianteId(Long estudianteId);

    boolean existsByMateriaId(Long materiaId);
}
