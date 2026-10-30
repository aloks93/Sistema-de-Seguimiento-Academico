package py.edu.upe.hernandarias.excel;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Descargas en Excel (.xlsx). Roles en SecurityConfig; el contenido respeta la jerarquía de cada rol. */
@RestController
@RequestMapping("/api/exportar")
@RequiredArgsConstructor
public class ExportacionController {

    private final ExportacionService exportacionService;

    @GetMapping("/estudiantes")
    public ResponseEntity<byte[]> estudiantes(@RequestParam(required = false) Long carreraId, Authentication authentication) {
        return exportacionService.estudiantes(carreraId, authentication.getName()).comoDescarga();
    }

    @GetMapping("/materias")
    public ResponseEntity<byte[]> materias(@RequestParam(required = false) Long carreraId, Authentication authentication) {
        return exportacionService.materias(carreraId, authentication.getName()).comoDescarga();
    }

    @GetMapping("/docentes")
    public ResponseEntity<byte[]> docentes() {
        return exportacionService.docentes().comoDescarga();
    }

    @GetMapping("/carreras")
    public ResponseEntity<byte[]> carreras(Authentication authentication) {
        return exportacionService.carreras(authentication.getName()).comoDescarga();
    }

    /** Con {@code materiaId}: planilla de esa materia. Sin él: todas las notas visibles. */
    @GetMapping("/notas")
    public ResponseEntity<byte[]> notas(@RequestParam(required = false) Long materiaId, Authentication authentication) {
        ArchivoExcel archivo = materiaId == null
                ? exportacionService.notas(authentication.getName())
                : exportacionService.planillaDeMateria(materiaId, authentication.getName());
        return archivo.comoDescarga();
    }

    @GetMapping("/mis-notas")
    public ResponseEntity<byte[]> misNotas(Authentication authentication) {
        return exportacionService.misNotas(authentication.getName()).comoDescarga();
    }
}
