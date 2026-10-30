package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Carrera;

/**
 * @param semestres   duración de la carrera
 * @param materias    cantidad de materias de la carrera
 * @param estudiantes cantidad de estudiantes inscriptos en la carrera
 */
public record CarreraResponse(Long id, String codigo, String nombre, int semestres, long materias, long estudiantes) {

    public static CarreraResponse from(Carrera carrera, long materias, long estudiantes) {
        return new CarreraResponse(carrera.getId(), carrera.getCodigo(), carrera.getNombre(), carrera.getSemestres(),
                materias, estudiantes);
    }
}
