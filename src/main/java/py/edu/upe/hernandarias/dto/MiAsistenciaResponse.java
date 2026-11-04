package py.edu.upe.hernandarias.dto;

import java.time.LocalDate;
import java.util.List;
import py.edu.upe.hernandarias.entity.EstadoAsistencia;

/** Asistencia del estudiante autenticado en una de sus materias, con el detalle de cada clase. */
public record MiAsistenciaResponse(
        Long materiaId,
        String codigoMateria,
        String nombreMateria,
        Integer semestre,
        String docenteNombre,
        int asistenciaMinima,
        long clasesDictadas,
        Integer clasesPrevistas,
        FilaAsistenciaResponse resumen,
        List<ClaseAsistida> clases) {

    public record ClaseAsistida(LocalDate fecha, String tema, EstadoAsistencia estado, String observacion) {
    }
}
