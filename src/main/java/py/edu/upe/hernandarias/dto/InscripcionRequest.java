package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Inscribe a uno o varios estudiantes (de la carrera de la materia) en una materia. */
public record InscripcionRequest(
        @NotNull Long materiaId,
        @NotEmpty @Size(max = 500) List<@NotNull Long> estudianteIds) {
}
