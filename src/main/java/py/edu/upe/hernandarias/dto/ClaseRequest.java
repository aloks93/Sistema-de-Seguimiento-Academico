package py.edu.upe.hernandarias.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import py.edu.upe.hernandarias.entity.EstadoAsistencia;

/**
 * Una clase con la asistencia de cada inscripto. Al editar, {@code materiaId} se ignora.
 *
 * @param registros un registro por estudiante; {@code estado = null} quita el registro de ese estudiante
 */
public record ClaseRequest(
        Long materiaId,
        @NotNull LocalDate fecha,
        @Size(max = 200) String tema,
        @NotNull @Size(max = 500) List<@Valid @NotNull Registro> registros) {

    public record Registro(
            @NotNull Long estudianteId,
            EstadoAsistencia estado,
            @Size(max = 200) String observacion) {
    }
}
