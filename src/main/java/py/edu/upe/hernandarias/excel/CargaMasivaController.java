package py.edu.upe.hernandarias.excel;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Plantilla Excel por carrera y carga masiva (solo ADMIN, ver SecurityConfig). */
@RestController
@RequestMapping("/api/carga-masiva")
@RequiredArgsConstructor
public class CargaMasivaController {

    private final CargaMasivaService cargaMasivaService;

    @GetMapping("/plantilla")
    public ResponseEntity<byte[]> plantilla(@RequestParam Long carreraId) {
        return cargaMasivaService.generarPlantilla(carreraId).comoDescarga();
    }

    /** Con {@code soloValidar=true} revisa el archivo sin guardar nada. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultadoImportacion importar(@RequestPart("archivo") MultipartFile archivo,
                                         @RequestParam(defaultValue = "false") boolean soloValidar,
                                         Authentication authentication) {
        return cargaMasivaService.importar(archivo, soloValidar, authentication.getName());
    }
}
