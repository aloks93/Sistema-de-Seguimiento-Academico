package py.edu.upe.hernandarias.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Exoneración de asistencia por causa justificada: el estudiante queda habilitado para el
 * examen final aunque no llegue al mínimo. Hasta {@link #MAXIMO_POR_SEMESTRE} materias por semestre.
 */
@Entity
@Table(
        name = "exoneraciones",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exoneracion_estudiante_materia",
                columnNames = {"estudiante_id", "materia_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exoneracion {

    public static final int MAXIMO_POR_SEMESTRE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "materia_id", nullable = false)
    private Materia materia;

    @Column(nullable = false, length = 300)
    private String motivo;

    /** Usuario del administrador que la otorgó. */
    @Column(name = "otorgada_por", nullable = false, length = 50)
    private String otorgadaPor;

    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;
}
