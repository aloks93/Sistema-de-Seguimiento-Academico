package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Para desactivar el 2FA se piden la contraseña y un código vigente. */
public record DesactivarDosFactoresRequest(
        @NotBlank String password,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "debe tener 6 dígitos") String codigo) {
}
