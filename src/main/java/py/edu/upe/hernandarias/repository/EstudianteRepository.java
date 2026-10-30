package py.edu.upe.hernandarias.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import py.edu.upe.hernandarias.entity.Estudiante;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    Optional<Estudiante> findByUsuarioUsername(String username);

    Optional<Estudiante> findByMatricula(String matricula);

    @EntityGraph(attributePaths = {"usuario", "carrera"})
    List<Estudiante> findAllByOrderByNombreCompletoAsc();

    @EntityGraph(attributePaths = {"usuario", "carrera"})
    List<Estudiante> findByCarreraIdOrderByNombreCompletoAsc(Long carreraId);

    boolean existsByMatricula(String matricula);

    boolean existsByMatriculaAndIdNot(String matricula, Long id);

    long countByCarreraId(Long carreraId);
}
