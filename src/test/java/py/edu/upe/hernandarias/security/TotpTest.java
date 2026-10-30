package py.edu.upe.hernandarias.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Códigos TOTP compatibles con Google Authenticator (vectores de prueba del RFC 6238). */
class TotpTest {

    private final Totp totp = new Totp();
    // Clave de los ejemplos del RFC 6238 ("12345678901234567890" en ASCII)
    private final String secretoRfc = Base32.codificar("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void generaLosCodigosDelRfc6238() {
        // El RFC publica 8 dígitos; Google Authenticator usa los últimos 6
        assertThat(totp.codigoEn(secretoRfc, 59)).isEqualTo("287082");
        assertThat(totp.codigoEn(secretoRfc, 1111111109)).isEqualTo("081804");
        assertThat(totp.codigoEn(secretoRfc, 1234567890)).isEqualTo("005924");
        assertThat(totp.codigoEn(secretoRfc, 2000000000)).isEqualTo("279037");
    }

    @Test
    void aceptaElCodigoVigenteYLosVecinosPeroNoOtros() {
        long ahora = 1_700_000_000L;
        assertThat(totp.verificar(secretoRfc, totp.codigoEn(secretoRfc, ahora), ahora)).isTrue();
        assertThat(totp.verificar(secretoRfc, totp.codigoEn(secretoRfc, ahora - 30), ahora)).isTrue();
        assertThat(totp.verificar(secretoRfc, totp.codigoEn(secretoRfc, ahora + 30), ahora)).isTrue();
        assertThat(totp.verificar(secretoRfc, totp.codigoEn(secretoRfc, ahora - 120), ahora)).isFalse();
        assertThat(totp.verificar(secretoRfc, "12345", ahora)).isFalse();
        assertThat(totp.verificar(secretoRfc, "abcdef", ahora)).isFalse();
        assertThat(totp.verificar(secretoRfc, null, ahora)).isFalse();
    }

    @Test
    void base32IdaYVuelta() {
        byte[] datos = {0, 1, 2, (byte) 0xfe, (byte) 0xff, 42, 7};
        assertThat(Base32.decodificar(Base32.codificar(datos))).isEqualTo(datos);
        assertThat(totp.generarSecreto()).matches("[A-Z2-7]{32}");
    }

    @Test
    void urlParaGoogleAuthenticator() {
        assertThat(totp.urlOtpauth("Seguimiento Académico", "ana", "ABC"))
                .isEqualTo("otpauth://totp/Seguimiento%20Acad%C3%A9mico%3Aana?secret=ABC"
                        + "&issuer=Seguimiento%20Acad%C3%A9mico&algorithm=SHA1&digits=6&period=30");
    }
}
