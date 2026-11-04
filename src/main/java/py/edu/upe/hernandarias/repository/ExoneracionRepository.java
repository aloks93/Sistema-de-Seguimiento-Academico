package py.edu.upe.hernandarias.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.edu.upe.hernandarias.entity.Exoneracion;

public interface ExoneracionRepository extends JpaRepository<Exoneracion, Long> {

    @EntityGraph(attributePaths = {"estudiante", "estudiante.carrera", "materia"})
    List<Exoneracion> findAllByOrderByFechaRegistroDesc();

    @EntityGraph(attributePaths = {"estudiante", "estudiante.carrera", "materia"})
    List<Exoneracion> findByMateriaDocenteUsuarioUsernameOrderByFechaRegistroDesc(String username);

    @EntityGraph(attributePaths = {"estudiante", "materia"})
    List<Exoneracion> findByMateriaIdIn(Collection<Long> materiaIds);

    List<Exoneracion> findByEstudianteId(Long estudianteId);

    Optional<Exoneracion> findByEstudianteIdAndMateriaId(Long estudianteId, Long materiaId);

    boolean existsByEstudianteIdAndMateriaId(Long estudianteId, Long materiaId);

    long countByEstudianteIdAndMateriaSemestre(Long estudianteId, Integer semestre);

    @Modifying
    @Query("delete from Exoneracion e where e.estudiante.id = :estudianteId")
    void eliminarDelEstudiante(@Param("estudianteId") Long estudianteId);

    @Modifying
    @Query("delete from Exoneracion e where e.materia.id = :materiaId")
    void eliminarDeLaMateria(@Param("materiaId") Long materiaId);
}
