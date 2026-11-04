package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import py.edu.upe.hernandarias.entity.Calificacion;

/** Igual que en el alta: la nota única o sus componentes (los componentes reemplazan a los anteriores). */
public record CalificacionUpdateRequest(
        @Min(Calificacion.NOTA_MINIMA) @Max(Calificacion.NOTA_MAXIMA) Integer nota,
        @Min(0) @Max(Calificacion.NOTA_MAXIMA) Integer primerParcial,
        @Min(0) @Max(Calificacion.NOTA_MAXIMA) Integer segundoParcial,
        @Min(0) @Max(Calificacion.NOTA_MAXIMA) Integer trabajoPractico,
        @Min(0) @Max(Calificacion.NOTA_MAXIMA) Integer examenFinal) {

    public ComponentesNota componentes() {
        return new ComponentesNota(nota, primerParcial, segundoParcial, trabajoPractico, examenFinal);
    }
}
