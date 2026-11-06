package py.edu.upe.hernandarias.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Pedido que llega al buzón de los administradores: "olvidé mi contraseña" o "quiero una cuenta".
 * El usuario se guarda como texto porque puede no existir (o haberse eliminado después).
 */
@Entity
@Table(name = "solicitudes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Solicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoSolicitud tipo;

    @Column(length = 50)
    private String username;

    @Column(length = 150)
    private String nombre;

    /** Correo o teléfono para avisarle a la persona. */
    @Column(length = 150)
    private String contacto;

    @Column(length = 500)
    private String mensaje;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EstadoSolicitud estado = EstadoSolicitud.PENDIENTE;

    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    /** Administrador que la resolvió o descartó. */
    @Column(name = "atendida_por", length = 50)
    private String atendidaPor;

    @Column(name = "fecha_atencion")
    private Instant fechaAtencion;
}
