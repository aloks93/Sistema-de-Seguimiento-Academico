package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Respuesta a un "olvidé mi contraseña": nueva contraseña para la cuenta del pedido.
 *
 * @param quitarDosFactores también quitarle la verificación en dos pasos (si perdió el celular)
 * @param activar           también reactivar la cuenta si estaba desactivada
 */
public record RestablecerRequest(
        @NotBlank @Size(min = 6, max = 72) String password,
        Boolean quitarDosFactores,
        Boolean activar) {
}
