package py.edu.upe.hernandarias.dto;

import java.time.LocalDate;
import java.util.Collection;
import py.edu.upe.hernandarias.entity.Asistencia;
import py.edu.upe.hernandarias.entity.Clase;
import py.edu.upe.hernandarias.entity.EstadoAsistencia;

/** Una clase con el conteo de cada estado. */
public record ClaseResponse(
        Long id,
        Long materiaId,
        LocalDate fecha,
        String tema,
        int presentes,
        int tardes,
        int ausentes,
        int justificados) {

    public static ClaseResponse from(Clase clase) {
        return from(clase, clase.getAsistencias());
    }

    public static ClaseResponse from(Clase clase, Collection<Asistencia> asistencias) {
        int[] conteo = new int[EstadoAsistencia.values().length];
        asistencias.forEach(asistencia -> conteo[asistencia.getEstado().ordinal()]++);
        return new ClaseResponse(clase.getId(), clase.getMateria().getId(), clase.getFecha(), clase.getTema(),
                conteo[EstadoAsistencia.PRESENTE.ordinal()],
                conteo[EstadoAsistencia.TARDE.ordinal()],
                conteo[EstadoAsistencia.AUSENTE.ordinal()],
                conteo[EstadoAsistencia.JUSTIFICADO.ordinal()]);
    }
}
