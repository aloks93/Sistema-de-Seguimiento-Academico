package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param docenteId        docente a cargo; {@code null} para dejarla sin asignar
 * @param asistenciaMinima mínimo propio de la materia; {@code null} = el de la carrera
 * @param clasesPrevistas  clases previstas en el semestre (opcional)
 */
public record MateriaRequest(
        @NotBlank @Size(max = 20) String codigo,
        @NotBlank @Size(max = 120) String nombre,
        @NotNull @Positive Integer creditos,
        @NotNull @Min(1) @Max(12) Integer semestre,
        @NotNull Long carreraId,
        Long docenteId,
        @Min(0) @Max(100) Integer asistenciaMinima,
        @Min(1) @Max(300) Integer clasesPrevistas) {
}
