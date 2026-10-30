package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param password nueva contraseña; {@code null} para no cambiarla
 */
public record EstudianteUpdateRequest(
        @NotBlank @Size(max = 20) String matricula,
        @NotBlank @Size(max = 150) String nombreCompleto,
        @NotNull Long carreraId,
        @Size(min = 6, max = 72) String password) {
}
