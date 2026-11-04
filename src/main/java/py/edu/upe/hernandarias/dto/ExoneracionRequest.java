package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExoneracionRequest(
        @NotNull Long estudianteId,
        @NotNull Long materiaId,
        @NotBlank @Size(max = 300) String motivo) {
}
