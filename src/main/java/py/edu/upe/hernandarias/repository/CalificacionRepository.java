package py.edu.upe.hernandarias.repository;

import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query("select c.estudiante.id from Calificacion c where c.materia.id = :materiaId")
    Set<Long> findEstudianteIdsByMateriaId(@Param("materiaId") Long materiaId);
    /** Notas de la materia con algún componente mayor que los puntajes indicados. */
    @Query("""
            select count(c) from Calificacion c where c.materia.id = :materiaId and (
                c.primerParcial > :primerParcial or c.segundoParcial > :segundoParcial
                or c.trabajoPractico > :trabajoPractico or c.examenFinal > :examenFinal)""")
    long contarQueSuperan(@Param("materiaId") Long materiaId, @Param("primerParcial") int primerParcial,
                          @Param("segundoParcial") int segundoParcial, @Param("trabajoPractico") int trabajoPractico,
                          @Param("examenFinal") int examenFinal);

    @Modifying
    @Query("delete from Calificacion c where c.estudiante.id = :estudianteId")
    void eliminarDelEstudiante(@Param("estudianteId") Long estudianteId);
}
