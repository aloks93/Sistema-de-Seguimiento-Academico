package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.Size;

/**
 * Cambios parciales sobre un usuario; los campos {@code null} no se modifican.
 *
 * @param quitarDosFactores {@code true} desactiva la verificación en dos pasos (p. ej. si perdió el celular)
 */
public record UsuarioUpdateRequest(
        Boolean activo,
        @Size(min = 6, max = 72) String password,
        Boolean quitarDosFactores) {
}
