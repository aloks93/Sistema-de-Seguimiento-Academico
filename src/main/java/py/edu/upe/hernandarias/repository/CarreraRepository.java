package py.edu.upe.hernandarias.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import py.edu.upe.hernandarias.entity.Carrera;

public interface CarreraRepository extends JpaRepository<Carrera, Long> {

    List<Carrera> findAllByOrderByNombreAsc();

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    boolean existsByNombre(String nombre);

    boolean existsByNombreAndIdNot(String nombre, Long id);
}
