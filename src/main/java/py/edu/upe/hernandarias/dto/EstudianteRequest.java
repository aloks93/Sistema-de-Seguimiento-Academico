package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Alta de un estudiante junto con el usuario con el que inicia sesión. */
public record EstudianteRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 6, max = 72) String password,
        @NotBlank @Size(max = 20) String matricula,
        @NotBlank @Size(max = 150) String nombreCompleto,
        @NotNull Long carreraId) {
}
