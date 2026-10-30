package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta de un docente junto con el usuario con el que inicia sesión. */
public record DocenteRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 6, max = 72) String password,
        @NotBlank @Size(max = 150) String nombreCompleto) {
}
