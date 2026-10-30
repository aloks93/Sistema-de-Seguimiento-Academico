package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Código de 6 dígitos de la app de autenticación. */
public record CodigoRequest(@NotBlank @Pattern(regexp = "\\d{6}", message = "debe tener 6 dígitos") String codigo) {
}
