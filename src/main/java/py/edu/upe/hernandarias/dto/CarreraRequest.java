package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CarreraRequest(
        @NotBlank @Size(max = 20) String codigo,
        @NotBlank @Size(max = 120) String nombre) {
}
