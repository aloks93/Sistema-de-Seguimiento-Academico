package py.edu.upe.hernandarias.dto;

import java.time.Instant;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Exoneracion;
import py.edu.upe.hernandarias.entity.Materia;

public record ExoneracionResponse(
        Long id,
        Long estudianteId,
        String matricula,
        String nombreEstudiante,
        String carreraNombre,
        Long materiaId,
        String codigoMateria,
        String nombreMateria,
        Integer semestre,
        String motivo,
        String otorgadaPor,
        Instant fechaRegistro) {

    public static ExoneracionResponse from(Exoneracion exoneracion) {
        Estudiante estudiante = exoneracion.getEstudiante();
        Materia materia = exoneracion.getMateria();
        return new ExoneracionResponse(exoneracion.getId(), estudiante.getId(), estudiante.getMatricula(),
                estudiante.getNombreCompleto(), estudiante.getCarrera().getNombre(), materia.getId(),
                materia.getCodigo(), materia.getNombre(), materia.getSemestre(), exoneracion.getMotivo(),
                exoneracion.getOtorgadaPor(), exoneracion.getFechaRegistro());
    }
}
