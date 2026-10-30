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
import org.springframework.web.bind.annotation.RestController;
import py.edu.upe.hernandarias.dto.CarreraRequest;
import py.edu.upe.hernandarias.dto.CarreraResponse;
import py.edu.upe.hernandarias.service.CarreraService;

/**
 * Consulta (DOCENTE: solo sus carreras; ADMIN: todas) y gestión (solo ADMIN) de carreras,
 * ver SecurityConfig.
 */
@RestController
@RequestMapping("/api/carreras")
@RequiredArgsConstructor
public class CarreraController {

    private final CarreraService carreraService;

    @GetMapping
    public List<CarreraResponse> listar(Authentication authentication) {
        return carreraService.listar(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<CarreraResponse> crear(@Valid @RequestBody CarreraRequest request) {
        CarreraResponse creada = carreraService.crear(request);
        return ResponseEntity.created(URI.create("/api/carreras/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    public CarreraResponse actualizar(@PathVariable Long id, @Valid @RequestBody CarreraRequest request) {
        return carreraService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        carreraService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
