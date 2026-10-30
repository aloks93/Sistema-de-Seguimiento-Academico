package py.edu.upe.hernandarias.excel;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Excel generado, listo para devolver como descarga. */
public record ArchivoExcel(String nombre, byte[] contenido) {

    public static final MediaType TIPO =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    public ResponseEntity<byte[]> comoDescarga() {
        return ResponseEntity.ok()
                .contentType(TIPO)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombre).build().toString())
                .body(contenido);
    }
}
