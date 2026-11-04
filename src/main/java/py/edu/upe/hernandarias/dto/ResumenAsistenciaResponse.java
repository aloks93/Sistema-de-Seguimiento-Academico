package py.edu.upe.hernandarias.dto;

import java.util.List;

/** Asistencia de todos los inscriptos de una materia, con su condición para el examen final. */
public record ResumenAsistenciaResponse(
        Long materiaId,
        String codigoMateria,
        String nombreMateria,
        int asistenciaMinima,
        long clasesDictadas,
        Integer clasesPrevistas,
        List<FilaAsistenciaResponse> estudiantes) {
}
