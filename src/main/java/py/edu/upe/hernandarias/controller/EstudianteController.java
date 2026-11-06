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
import py.edu.upe.hernandarias.dto.CalificacionResponse;
import py.edu.upe.hernandarias.dto.EstudianteRequest;
import py.edu.upe.hernandarias.dto.EstudianteResponse;
import py.edu.upe.hernandarias.dto.EstudianteUpdateRequest;
import py.edu.upe.hernandarias.dto.MiAsistenciaResponse;
import py.edu.upe.hernandarias.service.AsistenciaService;
import py.edu.upe.hernandarias.service.CalificacionService;
import py.edu.upe.hernandarias.service.EstudianteService;

/**
 * Consulta, alta y edición (ADMIN; DOCENTE solo en las carreras donde tiene materias) y
 * baja (solo ADMIN) de estudiantes, ver SecurityConfig y PermisosService.
 */
@RestController
@RequestMapping("/api/estudiantes")
@RequiredArgsConstructor
public class EstudianteController {

    private final CalificacionService calificacionService;
    private final EstudianteService estudianteService;
    private final AsistenciaService asistenciaService;

    /** Notas del estudiante autenticado (rol ESTUDIANTE, ver SecurityConfig). */
    @GetMapping("/me/calificaciones")
    public List<CalificacionResponse> misCalificaciones(Authentication authentication) {
        return calificacionService.listarPorUsuario(authentication.getName());
    }

    /** Asistencia del estudiante autenticado en cada materia que cursa (rol ESTUDIANTE). */
    @GetMapping("/me/asistencia")
    public List<MiAsistenciaResponse> miAsistencia(Authentication authentication) {
        return asistenciaService.miAsistencia(authentication.getName());
    }

    @GetMapping
    public List<EstudianteResponse> listar(@RequestParam(required = false) Long carreraId,
                                           Authentication authentication) {
        return estudianteService.listar(carreraId, authentication.getName());
    }

    @PostMapping
    public ResponseEntity<EstudianteResponse> crear(@Valid @RequestBody EstudianteRequest request,
                                                    Authentication authentication) {
        EstudianteResponse creado = estudianteService.crear(request, authentication.getName());
        return ResponseEntity.created(URI.create("/api/estudiantes/" + creado.id())).body(creado);
    }

    @PutMapping("/{id}")
    public EstudianteResponse actualizar(@PathVariable Long id,
                                         @Valid @RequestBody EstudianteUpdateRequest request,
                                         Authentication authentication) {
        return estudianteService.actualizar(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id,
                                         @RequestParam(defaultValue = "false") boolean forzar) {
        estudianteService.eliminar(id, forzar);
        return ResponseEntity.noContent().build();
    }
}
