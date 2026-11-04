package py.edu.upe.hernandarias.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.edu.upe.hernandarias.entity.Inscripcion;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    @EntityGraph(attributePaths = {"estudiante", "estudiante.usuario"})
    List<Inscripcion> findByMateriaIdOrderByEstudianteNombreCompletoAsc(Long materiaId);

    @EntityGraph(attributePaths = {"estudiante", "materia"})
    List<Inscripcion> findByMateriaIdIn(Collection<Long> materiaIds);

    @EntityGraph(attributePaths = {"materia", "materia.carrera", "materia.docente"})
    List<Inscripcion> findByEstudianteIdOrderByMateriaSemestreAscMateriaNombreAsc(Long estudianteId);

    @EntityGraph(attributePaths = {"estudiante", "materia", "materia.docente", "materia.docente.usuario"})
    Optional<Inscripcion> findWithMateriaById(Long id);

    boolean existsByEstudianteIdAndMateriaId(Long estudianteId, Long materiaId);

    boolean existsByEstudianteId(Long estudianteId);

    boolean existsByMateriaId(Long materiaId);

    long countByMateriaId(Long materiaId);

    /** Cantidad de inscriptos por materia: filas [materiaId, cantidad]. */
    @Query("select i.materia.id, count(i) from Inscripcion i group by i.materia.id")
    List<Object[]> contarPorMateria();

    @Modifying
    @Query("delete from Inscripcion i where i.estudiante.id = :estudianteId")
    void eliminarDelEstudiante(@Param("estudianteId") Long estudianteId);

    @Modifying
    @Query("delete from Inscripcion i where i.materia.id = :materiaId")
    void eliminarDeLaMateria(@Param("materiaId") Long materiaId);
}
