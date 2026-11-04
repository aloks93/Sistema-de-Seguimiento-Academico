package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Exoneracion;
import py.edu.upe.hernandarias.service.CalculoAsistencia;

/**
 * Asistencia de un estudiante en una materia.
 *
 * @param exoneracionId {@code null} si no está exonerado
 */
public record FilaAsistenciaResponse(
        Long estudianteId,
        String matricula,
        String nombreEstudiante,
        int presentes,
        int tardes,
        int ausentes,
        int justificados,
        int registradas,
        int ausenciasComputadas,
        Double porcentaje,
        Integer faltasRestantes,
        CalculoAsistencia.Condicion condicion,
        Long exoneracionId,
        String motivoExoneracion) {

    public static FilaAsistenciaResponse from(Estudiante estudiante, CalculoAsistencia.Resultado r, Exoneracion exoneracion) {
        return new FilaAsistenciaResponse(estudiante.getId(), estudiante.getMatricula(), estudiante.getNombreCompleto(),
                r.presentes(), r.tardes(), r.ausentes(), r.justificados(), r.registradas(), r.ausenciasComputadas(),
                r.porcentaje(), r.faltasRestantes(), r.condicion(),
                exoneracion == null ? null : exoneracion.getId(),
                exoneracion == null ? null : exoneracion.getMotivo());
    }
}
