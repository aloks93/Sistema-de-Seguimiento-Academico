package py.edu.upe.hernandarias.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import py.edu.upe.hernandarias.dto.CalificacionResponse;
import py.edu.upe.hernandarias.service.CalificacionService;

@RestController
@RequestMapping("/api/estudiantes")
@RequiredArgsConstructor
public class EstudianteController {

    private final CalificacionService calificacionService;

    /** Notas del estudiante autenticado (rol ESTUDIANTE, ver SecurityConfig). */
    @GetMapping("/me/calificaciones")
    public List<CalificacionResponse> misCalificaciones(Authentication authentication) {
        return calificacionService.listarPorUsuario(authentication.getName());
    }
}
