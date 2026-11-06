package py.edu.upe.hernandarias.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import py.edu.upe.hernandarias.entity.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    /** Para el buzón: la persona puede escribir su usuario con otras mayúsculas. */
    Optional<Usuario> findFirstByUsernameIgnoreCase(String username);

    boolean existsByUsername(String username);

    List<Usuario> findAllByOrderByRolAscUsernameAsc();
}
