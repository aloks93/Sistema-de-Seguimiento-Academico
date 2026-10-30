package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.Size;

/**
 * Cambios parciales sobre un usuario; los campos {@code null} no se modifican.
 */
public record UsuarioUpdateRequest(
        Boolean activo,
        @Size(min = 6, max = 72) String password) {
}
