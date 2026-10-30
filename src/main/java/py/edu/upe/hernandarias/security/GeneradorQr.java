package py.edu.upe.hernandarias.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Dibuja códigos QR como SVG (se muestran en la página como imagen data:). */
@Component
public class GeneradorQr {

    public String comoDataUri(String contenido) {
        BitMatrix matriz;
        try {
            // Con tamaño 0 ZXing devuelve un módulo por celda (el SVG escala sin perder nitidez)
            matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.MARGIN, 2,
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M));
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el código QR", e);
        }

        int ancho = matriz.getWidth();
        int alto = matriz.getHeight();
        StringBuilder trazo = new StringBuilder();
        for (int y = 0; y < alto; y++) {
            for (int x = 0; x < ancho; x++) {
                if (matriz.get(x, y)) {
                    trazo.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                }
            }
        }
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" shape-rendering=\"crispEdges\">"
                .formatted(ancho, alto)
                + "<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>"
                + "<path fill=\"#000000\" d=\"" + trazo + "\"/></svg>";
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
    }
}
