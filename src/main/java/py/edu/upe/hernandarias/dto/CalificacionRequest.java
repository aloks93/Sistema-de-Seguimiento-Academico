package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import py.edu.upe.hernandarias.entity.Calificacion;

/**
 * Nota de un estudiante en una materia. Se envía la nota única ({@code nota}) o sus componentes
 * (dos parciales, trabajo práctico y examen final); con componentes, la nota es su suma.
 */
public record CalificacionRequest(
        @NotNull Long estudianteId,
        @NotNull Long materiaId,
        @Min(Calificacion.NOTA_MINIMA) @Max(Calificacion.NOTA_MAXIMA) Integer nota,
        @Min(0) @Max(Calificacion.MAXIMO_PARCIAL) Integer primerParcial,
        @Min(0) @Max(Calificacion.MAXIMO_PARCIAL) Integer segundoParcial,
        @Min(0) @Max(Calificacion.MAXIMO_TRABAJO_PRACTICO) Integer trabajoPractico,
        @Min(0) @Max(Calificacion.MAXIMO_EXAMEN_FINAL) Integer examenFinal) {

    public ComponentesNota componentes() {
        return new ComponentesNota(nota, primerParcial, segundoParcial, trabajoPractico, examenFinal);
    }
}
