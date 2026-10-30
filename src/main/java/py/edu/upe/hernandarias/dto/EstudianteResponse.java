package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.entity.Estudiante;

public record EstudianteResponse(
        Long id,
        String username,
        String matricula,
        String nombreCompleto,
        Long carreraId,
        String carreraCodigo,
        String carreraNombre,
        boolean activo) {

    public static EstudianteResponse from(Estudiante estudiante) {
        Carrera carrera = estudiante.getCarrera();
        return new EstudianteResponse(
                estudiante.getId(),
                estudiante.getUsuario().getUsername(),
                estudiante.getMatricula(),
                estudiante.getNombreCompleto(),
                carrera.getId(),
                carrera.getCodigo(),
                carrera.getNombre(),
                estudiante.getUsuario().isActivo());
    }
}
