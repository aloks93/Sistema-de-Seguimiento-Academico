package py.edu.upe.hernandarias.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import py.edu.upe.hernandarias.entity.Docente;

public interface DocenteRepository extends JpaRepository<Docente, Long> {

    @EntityGraph(attributePaths = "usuario")
    List<Docente> findAllByOrderByNombreCompletoAsc();
}
