package py.edu.upe.hernandarias.dto;

import java.util.List;
import py.edu.upe.hernandarias.entity.Docente;

/**
 * @param materias códigos de las materias a cargo del docente
 */
public record DocenteResponse(
        Long id,
        String username,
        String nombreCompleto,
        boolean activo,
        List<String> materias) {

    public static DocenteResponse from(Docente docente, List<String> materias) {
        return new DocenteResponse(
                docente.getId(),
                docente.getUsuario().getUsername(),
                docente.getNombreCompleto(),
                docente.getUsuario().isActivo(),
                materias);
    }
}
