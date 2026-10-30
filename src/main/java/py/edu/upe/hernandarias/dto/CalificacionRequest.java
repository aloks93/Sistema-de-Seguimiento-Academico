package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import py.edu.upe.hernandarias.entity.Calificacion;

public record CalificacionRequest(
        @NotNull Long estudianteId,
        @NotNull Long materiaId,
        @NotNull @Min(Calificacion.NOTA_MINIMA) @Max(Calificacion.NOTA_MAXIMA) Integer nota) {
}
