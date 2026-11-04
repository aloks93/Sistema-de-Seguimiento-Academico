package py.edu.upe.hernandarias.dto;

import java.util.List;
import py.edu.upe.hernandarias.service.CalculoAsistencia;

/**
 * Indicadores de asistencia de las materias visibles para el usuario (todas para un ADMIN).
 *
 * @param promedioAsistencia promedio de los porcentajes de los estudiantes con clases registradas
 * @param alertas            estudiantes en riesgo o no habilitados, de menor a mayor asistencia
 */
public record ReporteAsistenciaResponse(
        long clases,
        long inscripciones,
        Double promedioAsistencia,
        long enRiesgo,
        long noHabilitados,
        long exonerados,
        List<PorMateria> materias,
        List<PorCarrera> carreras,
        List<Alerta> alertas) {

    public record PorMateria(Long materiaId, String codigo, String nombre, String carreraNombre, String docenteNombre,
                             long inscriptos, long clasesDictadas, Integer clasesPrevistas, int asistenciaMinima,
                             Double promedioAsistencia, long habilitados, long enRiesgo, long noHabilitados,
                             long exonerados) {
    }

    public record PorCarrera(Long carreraId, String nombre, long materias, long inscripciones,
                             Double promedioAsistencia, long enRiesgo, long noHabilitados) {
    }

    public record Alerta(Long estudianteId, String matricula, String nombreEstudiante, Long materiaId,
                         String codigoMateria, String nombreMateria, Double porcentaje, Integer faltasRestantes,
                         int asistenciaMinima, CalculoAsistencia.Condicion condicion) {
    }
}
