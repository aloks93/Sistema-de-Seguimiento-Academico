package py.edu.upe.hernandarias.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import py.edu.upe.hernandarias.entity.Materia;

public interface MateriaRepository extends JpaRepository<Materia, Long> {

    Optional<Materia> findByCodigo(String codigo);

    @EntityGraph(attributePaths = {"carrera", "docente", "docente.usuario"})
    List<Materia> findAllByOrderBySemestreAscNombreAsc();

    @EntityGraph(attributePaths = {"carrera", "docente", "docente.usuario"})
    List<Materia> findByCarreraIdOrderBySemestreAscNombreAsc(Long carreraId);

    @EntityGraph(attributePaths = {"carrera", "docente", "docente.usuario"})
    List<Materia> findByDocenteUsuarioUsernameOrderBySemestreAscNombreAsc(String username);

    @EntityGraph(attributePaths = "docente")
    List<Materia> findByDocenteIsNotNullOrderByCodigoAsc();

    @Query("select max(m.semestre) from Materia m where m.carrera.id = :carreraId")
    Integer findMaxSemestreByCarreraId(@Param("carreraId") Long carreraId);

    @Query("select distinct m.carrera.id from Materia m where m.docente.usuario.username = :username")
    Set<Long> findCarreraIdsByDocenteUsername(@Param("username") String username);

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    boolean existsByDocenteId(Long docenteId);

    long countByCarreraId(Long carreraId);
}
