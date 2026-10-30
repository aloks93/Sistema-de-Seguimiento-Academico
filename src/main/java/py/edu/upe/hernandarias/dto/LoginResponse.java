package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Rol;

/**
 * @param token          JWT a enviar en el header {@code Authorization: Bearer <token>};
 *                       {@code null} si todavía falta el código de verificación
 * @param tipo           siempre {@code Bearer}
 * @param expiraEn       segundos de validez del token
 * @param requiereCodigo la contraseña es correcta pero el usuario tiene verificación en dos pasos:
 *                       hay que repetir el login enviando también el {@code codigo}
 */
public record LoginResponse(String token, String tipo, long expiraEn, String username, Rol rol, boolean requiereCodigo) {

    public static LoginResponse bearer(String token, long expiraEn, String username, Rol rol) {
        return new LoginResponse(token, "Bearer", expiraEn, username, rol, false);
    }

    public static LoginResponse faltaCodigo(String username) {
        return new LoginResponse(null, null, 0, username, null, true);
    }
}
