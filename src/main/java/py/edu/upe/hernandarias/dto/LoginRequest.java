package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param codigo código de 6 dígitos de la app de autenticación; solo para usuarios con
 *               verificación en dos pasos activa (si falta, la respuesta lo pide)
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password,
        String codigo) {
}
