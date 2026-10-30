package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambioContrasenaRequest(
        @NotBlank String actual,
        @NotBlank @Size(min = 6, max = 72) String nueva) {
}
