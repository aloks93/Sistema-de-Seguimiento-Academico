package py.edu.upe.hernandarias.dto;

import java.util.List;
import py.edu.upe.hernandarias.entity.EstadoAsistencia;

/** Lista de una clase: todos los inscriptos de la materia, con su estado ({@code null} = sin registrar). */
public record ClaseDetalleResponse(ClaseResponse clase, List<Registro> registros) {

    public record Registro(Long estudianteId, String matricula, String nombreEstudiante,
                           EstadoAsistencia estado, String observacion) {
    }
}
