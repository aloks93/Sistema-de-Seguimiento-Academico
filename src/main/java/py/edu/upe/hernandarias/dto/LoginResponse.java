package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Rol;

/**
 * @param token     JWT a enviar en el header {@code Authorization: Bearer <token>}
 * @param tipo      siempre {@code Bearer}
 * @param expiraEn  segundos de validez del token
 */
public record LoginResponse(String token, String tipo, long expiraEn, String username, Rol rol) {

    public static LoginResponse bearer(String token, long expiraEn, String username, Rol rol) {
        return new LoginResponse(token, "Bearer", expiraEn, username, rol);
    }
}
