package py.edu.upe.hernandarias.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import py.edu.upe.hernandarias.entity.EstadoSolicitud;
import py.edu.upe.hernandarias.entity.Solicitud;
import py.edu.upe.hernandarias.entity.TipoSolicitud;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    List<Solicitud> findAllByOrderByFechaRegistroDesc();

    List<Solicitud> findByEstadoOrderByFechaRegistroDesc(EstadoSolicitud estado);

    long countByEstado(EstadoSolicitud estado);

    /** Pedido pendiente del mismo tipo y usuario: se actualiza en vez de repetirlo. */
    Optional<Solicitud> findFirstByTipoAndUsernameIgnoreCaseAndEstado(TipoSolicitud tipo, String username,
                                                                     EstadoSolicitud estado);
}
