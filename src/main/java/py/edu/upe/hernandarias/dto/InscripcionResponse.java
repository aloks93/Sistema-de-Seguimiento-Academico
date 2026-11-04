package py.edu.upe.hernandarias.dto;

import java.time.Instant;

/**
 * @param tieneRegistros {@code true} si ya tiene notas o asistencia en la materia
 *                       (entonces no se puede quitar la inscripción)
 */
public record InscripcionResponse(
        Long id,
        Long estudianteId,
        String matricula,
        String nombreCompleto,
        String username,
        Long materiaId,
        Instant fechaRegistro,
        boolean tieneRegistros) {
}
