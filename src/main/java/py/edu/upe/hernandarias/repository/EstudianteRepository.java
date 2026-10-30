package py.edu.upe.hernandarias.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import py.edu.upe.hernandarias.entity.Estudiante;

public interface EstudianteRepository extends JpaRepository<Estudiante, Long> {

    Optional<Estudiante> findByUsuarioUsername(String username);

    Optional<Estudiante> findByMatricula(String matricula);
}
