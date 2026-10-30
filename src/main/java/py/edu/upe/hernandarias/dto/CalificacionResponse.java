package py.edu.upe.hernandarias.dto;

import java.time.Instant;
import py.edu.upe.hernandarias.entity.Calificacion;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Materia;

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
        Integer nota,
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
                calificacion.getNota(),
                calificacion.getFechaRegistro(),
                calificacion.getFechaActualizacion());
    }
}
