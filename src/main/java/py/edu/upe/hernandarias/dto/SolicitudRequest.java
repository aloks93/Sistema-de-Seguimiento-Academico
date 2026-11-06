package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import py.edu.upe.hernandarias.entity.TipoSolicitud;

/**
 * Pedido enviado desde la pantalla de inicio de sesión (sin estar logueado).
 * CONTRASENA exige el usuario; ACCESO exige el nombre completo.
 */
public record SolicitudRequest(
        @NotNull TipoSolicitud tipo,
        @Size(max = 50) String username,
        @Size(max = 150) String nombre,
        @Size(max = 150) String contacto,
        @Size(max = 500) String mensaje) {
}
