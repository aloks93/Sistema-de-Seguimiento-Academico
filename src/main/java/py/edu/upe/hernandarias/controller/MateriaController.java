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
import py.edu.upe.hernandarias.dto.MateriaRequest;
import py.edu.upe.hernandarias.dto.MateriaResponse;
import py.edu.upe.hernandarias.service.MateriaService;

/**
 * Consulta (DOCENTE: solo las que tiene a cargo; ADMIN: todas) y gestión (solo ADMIN) de materias,
 * ver SecurityConfig.
 */
@RestController
@RequestMapping("/api/materias")
@RequiredArgsConstructor
public class MateriaController {

    private final MateriaService materiaService;

    @GetMapping
    public List<MateriaResponse> listar(@RequestParam(required = false) Long carreraId,
                                        Authentication authentication) {
        return materiaService.listar(carreraId, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<MateriaResponse> crear(@Valid @RequestBody MateriaRequest request) {
        MateriaResponse creada = materiaService.crear(request);
        return ResponseEntity.created(URI.create("/api/materias/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}")
    public MateriaResponse actualizar(@PathVariable Long id, @Valid @RequestBody MateriaRequest request) {
        return materiaService.actualizar(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        materiaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
