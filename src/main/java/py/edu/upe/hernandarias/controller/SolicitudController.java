package py.edu.upe.hernandarias.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import py.edu.upe.hernandarias.dto.EstadoSolicitudRequest;
import py.edu.upe.hernandarias.dto.RestablecerRequest;
import py.edu.upe.hernandarias.dto.SolicitudRequest;
import py.edu.upe.hernandarias.dto.SolicitudResponse;
import py.edu.upe.hernandarias.entity.EstadoSolicitud;
import py.edu.upe.hernandarias.security.LimiteSolicitudes;
import py.edu.upe.hernandarias.service.SolicitudService;

/**
 * Buzón de pedidos. Enviar es público (desde la pantalla de inicio de sesión); el resto es
 * solo para ADMIN (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/solicitudes")
@RequiredArgsConstructor
public class SolicitudController {

    private final SolicitudService solicitudService;
    private final LimiteSolicitudes limiteSolicitudes;

    /** Siempre responde lo mismo, exista o no el usuario, para no revelar qué cuentas hay. */
    @PostMapping
    public ResponseEntity<Map<String, String>> enviar(@Valid @RequestBody SolicitudRequest request,
                                                      HttpServletRequest http) {
        limiteSolicitudes.registrar(http.getRemoteAddr());
        solicitudService.enviar(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("mensaje",
                "Pedido enviado. Un administrador lo revisará y se comunicará con usted."));
    }

    @GetMapping
    public List<SolicitudResponse> listar(@RequestParam(required = false) EstadoSolicitud estado) {
        return solicitudService.listar(estado);
    }

    @GetMapping("/pendientes")
    public Map<String, Long> pendientes() {
        return Map.of("cantidad", solicitudService.contarPendientes());
    }

    @PatchMapping("/{id}")
    public SolicitudResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoSolicitudRequest request,
                                           Authentication authentication) {
        return solicitudService.cambiarEstado(id, request.estado(), authentication.getName());
    }

    @PostMapping("/{id}/restablecer")
    public SolicitudResponse restablecer(@PathVariable Long id, @Valid @RequestBody RestablecerRequest request,
                                         Authentication authentication) {
        return solicitudService.restablecer(id, request, authentication.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        solicitudService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
