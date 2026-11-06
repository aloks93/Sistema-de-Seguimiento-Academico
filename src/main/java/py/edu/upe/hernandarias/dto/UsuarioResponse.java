package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;

/**
 * Cuenta de acceso tal como la ve un administrador.
 *
 * @param nombre     nombre completo del docente o estudiante; el usuario para un ADMIN
 * @param detalle    matrícula y carrera del estudiante, o {@code null}
 * @param superAdmin si es el super administrador
 */
public record UsuarioResponse(Long id, String username, Rol rol, boolean activo, boolean dosFactores,
                              boolean superAdmin, String nombre, String detalle) {

    public static UsuarioResponse from(Usuario usuario) {
        return from(usuario, usuario.getUsername(), null);
    }

    public static UsuarioResponse from(Usuario usuario, String nombre, String detalle) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getRol(), usuario.isActivo(),
                usuario.tieneDosFactores(), usuario.isSuperAdmin(), nombre, detalle);
    }
}
