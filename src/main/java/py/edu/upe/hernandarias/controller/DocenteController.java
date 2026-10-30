package py.edu.upe.hernandarias.controller;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import py.edu.upe.hernandarias.dto.DocenteRequest;
import py.edu.upe.hernandarias.dto.DocenteResponse;
import py.edu.upe.hernandarias.dto.DocenteUpdateRequest;
import py.edu.upe.hernandarias.service.DocenteService;

/** Gestión de docentes (solo ADMIN, ver SecurityConfig). */
@RestController
@RequestMapping("/api/docentes")
@RequiredArgsConstructor
public class DocenteController {

    private final DocenteService docenteService;

    @GetMapping
    public List<DocenteResponse> listar() {
        return docenteService.listar();
    }

    @PostMapping
    public ResponseEntity<DocenteResponse> crear(@Valid @RequestBody DocenteRequest request) {
        DocenteResponse creado = docenteService.crear(request);
        return ResponseEntity.created(URI.create("/api/docentes/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public DocenteResponse actualizar(@PathVariable Long id, @Valid @RequestBody DocenteUpdateRequest request) {
        return docenteService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        docenteService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
