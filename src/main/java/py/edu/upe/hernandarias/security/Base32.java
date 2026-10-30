package py.edu.upe.hernandarias.security;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

/** Codificación Base32 (RFC 4648, sin relleno), el formato de las claves de Google Authenticator. */
final class Base32 {

    private static final String ALFABETO = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32() {
    }

    static String codificar(byte[] datos) {
        StringBuilder salida = new StringBuilder((datos.length * 8 + 4) / 5);
        int buffer = 0;
        int bits = 0;
        for (byte dato : datos) {
            buffer = (buffer << 8) | (dato & 0xff);
            bits += 8;
            while (bits >= 5) {
                salida.append(ALFABETO.charAt((buffer >> (bits - 5)) & 0x1f));
                bits -= 5;
            }
        }
        if (bits > 0) {
            salida.append(ALFABETO.charAt((buffer << (5 - bits)) & 0x1f));
        }
        return salida.toString();
    }

    static byte[] decodificar(String texto) {
        String limpio = texto.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char caracter : limpio.toCharArray()) {
            int valor = ALFABETO.indexOf(caracter);
            if (valor < 0) {
                throw new IllegalArgumentException("Carácter inválido en Base32: " + caracter);
            }
            buffer = (buffer << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                salida.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return salida.toByteArray();
    }
}
