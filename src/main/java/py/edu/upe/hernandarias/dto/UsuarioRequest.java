package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de un usuario ADMIN. Los docentes se crean con {@link DocenteRequest}
 * y los estudiantes con {@link EstudianteRequest}.
 */
public record UsuarioRequest(
        @NotBlank @Size(max = 50) String username,
        @NotBlank @Size(min = 6, max = 72) String password) {
}
