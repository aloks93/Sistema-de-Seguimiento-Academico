package py.edu.upe.hernandarias.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "carreras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Carrera {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, unique = true, length = 120)
    private String nombre;

    /** Duración de la carrera; ninguna materia puede estar en un semestre mayor. */
    @Min(1)
    @Max(12)
    @Builder.Default
    @Column(nullable = false)
    private Integer semestres = 10;

    /** Reglamento de la UPE: 70 % de las clases. */
    public static final int ASISTENCIA_MINIMA_POR_DEFECTO = 70;

    /** Porcentaje mínimo de asistencia para rendir el examen final (cada materia puede tener el suyo). */
    @Min(0)
    @Max(100)
    @Builder.Default
    @Column(name = "asistencia_minima", nullable = false)
    private Integer asistenciaMinima = ASISTENCIA_MINIMA_POR_DEFECTO;
}
