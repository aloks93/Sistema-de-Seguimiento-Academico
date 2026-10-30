package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;

public record UsuarioResponse(Long id, String username, Rol rol, boolean activo, boolean dosFactores) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getRol(), usuario.isActivo(),
                usuario.tieneDosFactores());
    }
}
