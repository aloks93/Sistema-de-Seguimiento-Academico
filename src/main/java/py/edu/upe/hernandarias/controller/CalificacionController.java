package py.edu.upe.hernandarias.controller;

import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import py.edu.upe.hernandarias.dto.CalificacionRequest;
import py.edu.upe.hernandarias.dto.CalificacionResponse;
import py.edu.upe.hernandarias.dto.CalificacionUpdateRequest;
import py.edu.upe.hernandarias.service.CalificacionService;

/** Registro y modificación de notas (roles DOCENTE y ADMIN, ver SecurityConfig). */
@RestController
@RequestMapping("/api/calificaciones")
@RequiredArgsConstructor
public class CalificacionController {

    private final CalificacionService calificacionService;

    @PostMapping
    public ResponseEntity<CalificacionResponse> registrar(@Valid @RequestBody CalificacionRequest request) {
        CalificacionResponse creada = calificacionService.registrar(request);
        return ResponseEntity.created(URI.create("/api/calificaciones/" + creada.id())).body(creada);
    }

    @PatchMapping("/{id}")
    public CalificacionResponse actualizar(@PathVariable Long id,
                                           @Valid @RequestBody CalificacionUpdateRequest request) {
        return calificacionService.actualizar(id, request);
    }
}
