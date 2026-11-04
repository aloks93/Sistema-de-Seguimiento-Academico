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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "materias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Materia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Positive
    @Column(nullable = false)
    private Integer creditos;

    @Min(1)
    @Max(12)
    @Column(nullable = false)
    private Integer semestre;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "carrera_id", nullable = false)
    private Carrera carrera;

    /** Docente a cargo; solo él (o un ADMIN) puede cargar notas de esta materia. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "docente_id")
    private Docente docente;

    /** Mínimo de asistencia propio de la materia; {@code null} = el de la carrera. */
    @Min(0)
    @Max(100)
    @Column(name = "asistencia_minima")
    private Integer asistenciaMinima;

    /** Clases previstas en el semestre (opcional): permite calcular las faltas que le quedan a cada alumno. */
    @Min(1)
    @Max(300)
    @Column(name = "clases_previstas")
    private Integer clasesPrevistas;

    // Cuánto vale cada componente de la nota. Lo define el docente a cargo (o un ADMIN) y suman 100;
    // por defecto, el reglamento de la UPE: dos parciales de 15, trabajo práctico de 20 y examen final de 50.
    public static final int PUNTAJE_PARCIAL_POR_DEFECTO = 15;
    public static final int PUNTAJE_TRABAJO_PRACTICO_POR_DEFECTO = 20;
    public static final int PUNTAJE_EXAMEN_FINAL_POR_DEFECTO = 50;

    @Min(0)
    @Max(100)
    @Builder.Default
    @Column(name = "puntaje_primer_parcial", nullable = false)
    private Integer puntajePrimerParcial = PUNTAJE_PARCIAL_POR_DEFECTO;

    @Min(0)
    @Max(100)
    @Builder.Default
    @Column(name = "puntaje_segundo_parcial", nullable = false)
    private Integer puntajeSegundoParcial = PUNTAJE_PARCIAL_POR_DEFECTO;

    @Min(0)
    @Max(100)
    @Builder.Default
    @Column(name = "puntaje_trabajo_practico", nullable = false)
    private Integer puntajeTrabajoPractico = PUNTAJE_TRABAJO_PRACTICO_POR_DEFECTO;

    @Min(1)
    @Max(100)
    @Builder.Default
    @Column(name = "puntaje_examen_final", nullable = false)
    private Integer puntajeExamenFinal = PUNTAJE_EXAMEN_FINAL_POR_DEFECTO;

    /** El mínimo que rige para esta materia. */
    public int asistenciaMinimaEfectiva() {
        return asistenciaMinima != null ? asistenciaMinima : carrera.getAsistenciaMinima();
    }
}
