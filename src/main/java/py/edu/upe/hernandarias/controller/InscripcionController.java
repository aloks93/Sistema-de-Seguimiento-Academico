package py.edu.upe.hernandarias.controller;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import py.edu.upe.hernandarias.dto.InscripcionRequest;
import py.edu.upe.hernandarias.dto.InscripcionResponse;
import py.edu.upe.hernandarias.dto.ResultadoInscripcion;
import py.edu.upe.hernandarias.service.InscripcionService;

/** Estudiantes inscriptos en cada materia (ADMIN o docente a cargo, ver InscripcionService). */
@RestController
@RequestMapping("/api/inscripciones")
@RequiredArgsConstructor
public class InscripcionController {

    private final InscripcionService inscripcionService;

    @GetMapping
    public List<InscripcionResponse> listar(@RequestParam Long materiaId, Authentication authentication) {
        return inscripcionService.listar(materiaId, authentication.getName());
    }

    @PostMapping
    public ResultadoInscripcion inscribir(@Valid @RequestBody InscripcionRequest request, Authentication authentication) {
        return inscripcionService.inscribir(request, authentication.getName());
    }

    /** Inscribe en la materia a todos los estudiantes activos de su carrera. */
    @PostMapping("/carrera-completa")
    public ResultadoInscripcion inscribirCarreraCompleta(@RequestParam Long materiaId, Authentication authentication) {
        return inscripcionService.inscribirCarreraCompleta(materiaId, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        inscripcionService.eliminar(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
