package py.edu.upe.hernandarias.dto;

import java.time.Instant;
import py.edu.upe.hernandarias.entity.Calificacion;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Materia;

/**
 * @param nota         puntaje total de 0 a 100
 * @param puntajes     cuánto vale cada componente en la materia
 * @param desglosada   {@code true} si se cargó por componentes (parciales, trabajo práctico, final)
 * @param calificacion escala de 1 a 5; {@code null} mientras falta el examen final de una nota desglosada
 */
public record CalificacionResponse(
        Long id,
        Long estudianteId,
        String matricula,
        String nombreEstudiante,
        Long materiaId,
        String codigoMateria,
        String nombreMateria,
        Integer creditos,
        Integer semestre,
        String docenteNombre,
        Integer nota,
        Integer primerParcial,
        Integer segundoParcial,
        Integer trabajoPractico,
        Integer examenFinal,
        PuntajesResponse puntajes,
        boolean desglosada,
        Integer calificacion,
        Instant fechaRegistro,
        Instant fechaActualizacion) {

    public static CalificacionResponse from(Calificacion calificacion) {
        Estudiante estudiante = calificacion.getEstudiante();
        Materia materia = calificacion.getMateria();
        return new CalificacionResponse(
                calificacion.getId(),
                estudiante.getId(),
                estudiante.getMatricula(),
                estudiante.getNombreCompleto(),
                materia.getId(),
                materia.getCodigo(),
                materia.getNombre(),
                materia.getCreditos(),
                materia.getSemestre(),
                materia.getDocente() == null ? null : materia.getDocente().getNombreCompleto(),
                calificacion.getNota(),
                calificacion.getPrimerParcial(),
                calificacion.getSegundoParcial(),
                calificacion.getTrabajoPractico(),
                calificacion.getExamenFinal(),
                PuntajesResponse.from(materia),
                calificacion.esDesglosada(),
                calificacion.calificacionFinal(),
                calificacion.getFechaRegistro(),
                calificacion.getFechaActualizacion());
    }
}
