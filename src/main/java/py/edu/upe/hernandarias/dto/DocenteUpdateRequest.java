package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param password nueva contraseña; {@code null} para no cambiarla
 */
public record DocenteUpdateRequest(
        @NotBlank @Size(max = 150) String nombreCompleto,
        @Size(min = 6, max = 72) String password) {
}
