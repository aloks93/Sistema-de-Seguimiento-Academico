package py.edu.upe.hernandarias.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import py.edu.upe.hernandarias.dto.ClaseDetalleResponse;
import py.edu.upe.hernandarias.dto.ClaseRequest;
import py.edu.upe.hernandarias.dto.ClaseResponse;
import py.edu.upe.hernandarias.dto.ExoneracionRequest;
import py.edu.upe.hernandarias.dto.ExoneracionResponse;
import py.edu.upe.hernandarias.dto.ReporteAsistenciaResponse;
import py.edu.upe.hernandarias.dto.ResumenAsistenciaResponse;
import py.edu.upe.hernandarias.service.AsistenciaService;

/**
 * Asistencia (roles DOCENTE y ADMIN, ver SecurityConfig): clases con su lista, resumen por materia,
 * reporte general y exoneraciones (estas últimas las otorga solo un ADMIN).
 */
@RestController
@RequestMapping("/api/asistencia")
@RequiredArgsConstructor
public class AsistenciaController {

    private final AsistenciaService asistenciaService;

    @GetMapping("/clases")
    public List<ClaseResponse> clases(@RequestParam Long materiaId, Authentication authentication) {
        return asistenciaService.listarClases(materiaId, authentication.getName());
    }

    @GetMapping("/clases/{id}")
    public ClaseDetalleResponse clase(@PathVariable Long id, Authentication authentication) {
        return asistenciaService.detalle(id, authentication.getName());
    }

    @PostMapping("/clases")
    public ResponseEntity<ClaseDetalleResponse> crearClase(@Valid @RequestBody ClaseRequest request,
                                                           Authentication authentication) {
        ClaseDetalleResponse creada = asistenciaService.crear(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/asistencia/clases/" + creada.clase().id())).body(creada);
    }

    @PutMapping("/clases/{id}")
    public ClaseDetalleResponse actualizarClase(@PathVariable Long id, @Valid @RequestBody ClaseRequest request,
                                                Authentication authentication) {
        return asistenciaService.actualizar(id, request, authentication.getName());
    }

    @DeleteMapping("/clases/{id}")
    public ResponseEntity<Void> eliminarClase(@PathVariable Long id, Authentication authentication) {
        asistenciaService.eliminarClase(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    /** Porcentaje y condición para el examen final de cada inscripto. */
    @GetMapping("/resumen")
    public ResumenAsistenciaResponse resumen(@RequestParam Long materiaId, Authentication authentication) {
        return asistenciaService.resumen(materiaId, authentication.getName());
    }

    @GetMapping("/reporte")
    public ReporteAsistenciaResponse reporte(Authentication authentication) {
        return asistenciaService.reporte(authentication.getName());
    }

    @GetMapping("/exoneraciones")
    public List<ExoneracionResponse> exoneraciones(Authentication authentication) {
        return asistenciaService.listarExoneraciones(authentication.getName());
    }

    @PostMapping("/exoneraciones")
    public ResponseEntity<ExoneracionResponse> exonerar(@Valid @RequestBody ExoneracionRequest request,
                                                        Authentication authentication) {
        ExoneracionResponse creada = asistenciaService.exonerar(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/asistencia/exoneraciones/" + creada.id())).body(creada);
    }

    @DeleteMapping("/exoneraciones/{id}")
    public ResponseEntity<Void> quitarExoneracion(@PathVariable Long id, Authentication authentication) {
        asistenciaService.quitarExoneracion(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
