package py.edu.upe.hernandarias.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import py.edu.upe.hernandarias.entity.Materia;

public interface MateriaRepository extends JpaRepository<Materia, Long> {

    Optional<Materia> findByCodigo(String codigo);

    @EntityGraph(attributePaths = {"carrera", "docente", "docente.usuario"})
    List<Materia> findAllByOrderBySemestreAscNombreAsc();

    @EntityGraph(attributePaths = {"carrera", "docente", "docente.usuario"})
    List<Materia> findByCarreraIdOrderBySemestreAscNombreAsc(Long carreraId);

    @EntityGraph(attributePaths = "docente")
    List<Materia> findByDocenteIsNotNullOrderByCodigoAsc();

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    boolean existsByDocenteId(Long docenteId);

    long countByCarreraId(Long carreraId);
}
