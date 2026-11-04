package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Materia;

/**
 * Los campos del docente son {@code null} si la materia no tiene docente asignado.
 *
 * @param asistenciaMinimaPropia mínimo propio de la materia ({@code null} = usa el de la carrera)
 * @param asistenciaMinima       mínimo que rige (el propio o el de la carrera)
 * @param inscriptos             estudiantes que cursan la materia
 */
public record MateriaResponse(
        Long id,
        String codigo,
        String nombre,
        Integer creditos,
        Integer semestre,
        Long carreraId,
        String carreraCodigo,
        String carreraNombre,
        Long docenteId,
        String docenteNombre,
        String docenteUsername,
        Integer asistenciaMinimaPropia,
        int asistenciaMinima,
        Integer clasesPrevistas,
        long inscriptos) {

    public static MateriaResponse from(Materia materia, long inscriptos) {
        Carrera carrera = materia.getCarrera();
        Docente docente = materia.getDocente();
        return new MateriaResponse(
                materia.getId(),
                materia.getCodigo(),
                materia.getNombre(),
                materia.getCreditos(),
                materia.getSemestre(),
                carrera.getId(),
                carrera.getCodigo(),
                carrera.getNombre(),
                docente == null ? null : docente.getId(),
                docente == null ? null : docente.getNombreCompleto(),
                docente == null ? null : docente.getUsuario().getUsername(),
                materia.getAsistenciaMinima(),
                materia.asistenciaMinimaEfectiva(),
                materia.getClasesPrevistas(),
                inscriptos);
    }
}
