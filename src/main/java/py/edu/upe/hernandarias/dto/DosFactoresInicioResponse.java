package py.edu.upe.hernandarias.dto;

/**
 * Datos para agregar la cuenta a Google Authenticator.
 *
 * @param secreto      clave para cargarla a mano si no se puede escanear el QR
 * @param urlOtpauth   dirección {@code otpauth://} que contiene el QR
 * @param qr           imagen del QR como {@code data:image/svg+xml;base64,...}
 * @param horaServidor hora del servidor (milisegundos desde 1970) para compararla con la del celular
 */
public record DosFactoresInicioResponse(String secreto, String urlOtpauth, String qr, long horaServidor) {
}
