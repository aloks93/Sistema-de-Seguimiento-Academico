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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "calificaciones",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_calificacion_estudiante_materia",
                columnNames = {"estudiante_id", "materia_id"}))
@Check(name = "ck_calificacion_nota_rango", constraints = "nota BETWEEN 0 AND 100")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Calificacion {

    public static final int NOTA_MINIMA = 0;
    public static final int NOTA_MAXIMA = 100;

    // Puntajes del reglamento de la UPE: dos parciales de 15, trabajo práctico de 20 y examen final de 50
    public static final int MAXIMO_PARCIAL = 15;
    public static final int MAXIMO_TRABAJO_PRACTICO = 20;
    public static final int MAXIMO_EXAMEN_FINAL = 50;
    /** Puntaje mínimo para aprobar (calificación 2). */
    public static final int PUNTAJE_APROBACION = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estudiante_id", nullable = false)
    private Estudiante estudiante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "materia_id", nullable = false)
    private Materia materia;

    @Min(NOTA_MINIMA)
    @Max(NOTA_MAXIMA)
    /** Puntaje total de 0 a 100. Si la nota está desglosada, es la suma de sus componentes. */
    @Column(nullable = false)
    private Integer nota;

    // Componentes (todos null = "nota única", cargada sin desglose)
    @Min(0)
    @Max(MAXIMO_PARCIAL)
    @Column(name = "primer_parcial")
    private Integer primerParcial;

    @Min(0)
    @Max(MAXIMO_PARCIAL)
    @Column(name = "segundo_parcial")
    private Integer segundoParcial;

    @Min(0)
    @Max(MAXIMO_TRABAJO_PRACTICO)
    @Column(name = "trabajo_practico")
    private Integer trabajoPractico;

    @Min(0)
    @Max(MAXIMO_EXAMEN_FINAL)
    @Column(name = "examen_final")
    private Integer examenFinal;

    @CreationTimestamp
    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private Instant fechaRegistro;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    public boolean esDesglosada() {
        return primerParcial != null || segundoParcial != null || trabajoPractico != null || examenFinal != null;
    }

    /**
     * Calificación final en la escala de 1 a 5; {@code null} mientras la materia está en curso
     * (nota desglosada todavía sin examen final).
     */
    public Integer calificacionFinal() {
        if (esDesglosada() && examenFinal == null) {
            return null;
        }
        return escala(nota);
    }

    /** Escala del reglamento: 1-59 = 1, 60-69 = 2, 70-79 = 3, 80-90 = 4, 91-100 = 5. */
    public static int escala(int puntaje) {
        if (puntaje < PUNTAJE_APROBACION) {
            return 1;
        }
        if (puntaje < 70) {
            return 2;
        }
        if (puntaje < 80) {
            return 3;
        }
        if (puntaje <= 90) {
            return 4;
        }
        return 5;
    }
}
