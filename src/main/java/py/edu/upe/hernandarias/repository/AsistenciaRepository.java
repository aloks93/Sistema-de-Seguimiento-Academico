package py.edu.upe.hernandarias.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import py.edu.upe.hernandarias.entity.Asistencia;

public interface AsistenciaRepository extends JpaRepository<Asistencia, Long> {

    @EntityGraph(attributePaths = {"clase", "estudiante"})
    List<Asistencia> findByClaseMateriaId(Long materiaId);

    @EntityGraph(attributePaths = {"clase", "clase.materia", "estudiante"})
    List<Asistencia> findByClaseMateriaIdIn(Collection<Long> materiaIds);

    @EntityGraph(attributePaths = {"clase", "clase.materia"})
    List<Asistencia> findByEstudianteId(Long estudianteId);

    @EntityGraph(attributePaths = "clase")
    List<Asistencia> findByEstudianteIdAndClaseMateriaId(Long estudianteId, Long materiaId);

    boolean existsByEstudianteId(Long estudianteId);

    boolean existsByEstudianteIdAndClaseMateriaId(Long estudianteId, Long materiaId);
}
