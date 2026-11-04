package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Cuánto vale cada componente de la nota en una materia. Los cuatro tienen que sumar 100
 * y el examen final vale al menos 1 punto (sin él la materia nunca se cierra).
 */
public record PuntajesRequest(
        @NotNull @Min(0) @Max(100) Integer primerParcial,
        @NotNull @Min(0) @Max(100) Integer segundoParcial,
        @NotNull @Min(0) @Max(100) Integer trabajoPractico,
        @NotNull @Min(1) @Max(100) Integer examenFinal) {

    public int suma() {
        return primerParcial + segundoParcial + trabajoPractico + examenFinal;
    }
}
