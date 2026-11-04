package py.edu.upe.hernandarias.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.edu.upe.hernandarias.entity.Clase;

public interface ClaseRepository extends JpaRepository<Clase, Long> {

    List<Clase> findByMateriaIdOrderByFechaDesc(Long materiaId);

    @EntityGraph(attributePaths = {"materia", "materia.carrera", "materia.docente", "materia.docente.usuario"})
    Optional<Clase> findWithMateriaById(Long id);

    boolean existsByMateriaIdAndFecha(Long materiaId, LocalDate fecha);

    boolean existsByMateriaIdAndFechaAndIdNot(Long materiaId, LocalDate fecha, Long id);

    boolean existsByMateriaId(Long materiaId);

    long countByMateriaId(Long materiaId);

    /** Clases dictadas por materia: filas [materiaId, cantidad]. */
    @Query("select c.materia.id, count(c) from Clase c where c.materia.id in :materiaIds group by c.materia.id")
    List<Object[]> contarPorMateria(@Param("materiaIds") Collection<Long> materiaIds);
}
