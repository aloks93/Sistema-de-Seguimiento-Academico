package py.edu.upe.hernandarias.security;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Códigos de un solo uso basados en tiempo (TOTP, RFC 6238): 6 dígitos, cambian cada 30 segundos,
 * HMAC-SHA1. Es el formato de Google Authenticator, Microsoft Authenticator, Authy, etc.
 */
@Component
public class Totp {

    private static final int DIGITOS = 6;
    private static final int PERIODO_SEGUNDOS = 30;
    /** Se aceptan el código anterior y el siguiente para tolerar relojes algo desfasados. */
    private static final int TOLERANCIA_PASOS = 1;

    private final SecureRandom aleatorio = new SecureRandom();

    /** Clave nueva de 160 bits en Base32. */
    public String generarSecreto() {
        byte[] clave = new byte[20];
        aleatorio.nextBytes(clave);
        return Base32.codificar(clave);
    }

    public boolean verificar(String secreto, String codigo) {
        return verificar(secreto, codigo, Instant.now().getEpochSecond());
    }

    boolean verificar(String secreto, String codigo, long instanteSegundos) {
        if (secreto == null || codigo == null || !codigo.matches("\\d{" + DIGITOS + "}")) {
            return false;
        }
        long paso = instanteSegundos / PERIODO_SEGUNDOS;
        byte[] esperado = codigo.getBytes(StandardCharsets.US_ASCII);
        for (int desfase = -TOLERANCIA_PASOS; desfase <= TOLERANCIA_PASOS; desfase++) {
            byte[] candidato = codigoEnPaso(secreto, paso + desfase).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(candidato, esperado)) {
                return true;
            }
        }
        return false;
    }

    /** Código vigente en un instante dado (lo usan los tests y el cálculo de verificación). */
    public String codigoEn(String secreto, long instanteSegundos) {
        return codigoEnPaso(secreto, instanteSegundos / PERIODO_SEGUNDOS);
    }

    String codigoEnPaso(String secreto, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(Base32.decodificar(secreto), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(paso).array());
            int desplazamiento = hash[hash.length - 1] & 0x0f;
            int binario = ((hash[desplazamiento] & 0x7f) << 24)
                    | ((hash[desplazamiento + 1] & 0xff) << 16)
                    | ((hash[desplazamiento + 2] & 0xff) << 8)
                    | (hash[desplazamiento + 3] & 0xff);
            return String.format("%0" + DIGITOS + "d", binario % (int) Math.pow(10, DIGITOS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 no disponible", e);
        }
    }

    /** Dirección otpauth:// que se codifica en el QR para que la app de autenticación la agregue. */
    public String urlOtpauth(String emisor, String cuenta, String secreto) {
        return "otpauth://totp/" + codificar(emisor + ":" + cuenta)
                + "?secret=" + secreto
                + "&issuer=" + codificar(emisor)
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PERIODO_SEGUNDOS;
    }

    private static String codificar(String texto) {
        return URLEncoder.encode(texto, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
