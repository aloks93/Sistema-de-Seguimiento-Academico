package py.edu.upe.hernandarias.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import py.edu.upe.hernandarias.dto.CalificacionRequest;
import py.edu.upe.hernandarias.dto.CalificacionResponse;
import py.edu.upe.hernandarias.dto.CalificacionUpdateRequest;
import py.edu.upe.hernandarias.service.CalificacionService;

/**
 * Consulta, registro, modificación y baja de notas (roles DOCENTE y ADMIN, ver SecurityConfig).
 * Un docente solo opera sobre las materias que tiene a cargo (ver CalificacionService).
 */
@RestController
@RequestMapping("/api/calificaciones")
@RequiredArgsConstructor
public class CalificacionController {

    private final CalificacionService calificacionService;

    @GetMapping
    public List<CalificacionResponse> listar(@RequestParam(required = false) Long estudianteId,
                                             @RequestParam(required = false) Long materiaId,
                                             Authentication authentication) {
        return calificacionService.listar(estudianteId, materiaId, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<CalificacionResponse> registrar(@Valid @RequestBody CalificacionRequest request,
                                                          Authentication authentication) {
        CalificacionResponse creada = calificacionService.registrar(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/calificaciones/" + creada.id())).body(creada);
    }

    @PatchMapping("/{id}")
    public CalificacionResponse actualizar(@PathVariable Long id,
                                           @Valid @RequestBody CalificacionUpdateRequest request,
                                           Authentication authentication) {
        return calificacionService.actualizar(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, Authentication authentication) {
        calificacionService.eliminar(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
