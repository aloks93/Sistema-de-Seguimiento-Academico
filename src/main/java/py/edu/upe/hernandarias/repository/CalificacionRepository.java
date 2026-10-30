package py.edu.upe.hernandarias.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import py.edu.upe.hernandarias.entity.Calificacion;

public interface CalificacionRepository extends JpaRepository<Calificacion, Long> {

    @EntityGraph(attributePaths = {"materia", "estudiante"})
    List<Calificacion> findByEstudianteIdOrderByMateriaSemestreAscMateriaNombreAsc(Long estudianteId);

    boolean existsByEstudianteIdAndMateriaId(Long estudianteId, Long materiaId);
}
