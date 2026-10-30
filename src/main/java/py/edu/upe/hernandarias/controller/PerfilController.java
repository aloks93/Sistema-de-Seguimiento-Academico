package py.edu.upe.hernandarias.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import py.edu.upe.hernandarias.dto.CambioContrasenaRequest;
import py.edu.upe.hernandarias.dto.PerfilResponse;
import py.edu.upe.hernandarias.dto.PreferenciasDto;
import py.edu.upe.hernandarias.service.PerfilService;

/** Perfil y ajustes del usuario autenticado (cualquier rol). */
@RestController
@RequestMapping("/api/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    @GetMapping
    public PerfilResponse obtener(Authentication authentication) {
        return perfilService.obtener(authentication.getName());
    }

    @PutMapping("/preferencias")
    public PreferenciasDto guardarPreferencias(@Valid @RequestBody PreferenciasDto preferencias,
                                               Authentication authentication) {
        return perfilService.guardarPreferencias(authentication.getName(), preferencias);
    }

    @PutMapping("/contrasena")
    public ResponseEntity<Void> cambiarContrasena(@Valid @RequestBody CambioContrasenaRequest request,
                                                  Authentication authentication) {
        perfilService.cambiarContrasena(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
