package py.edu.upe.hernandarias.dto;

import java.time.Instant;
import py.edu.upe.hernandarias.entity.EstadoSolicitud;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Solicitud;
import py.edu.upe.hernandarias.entity.TipoSolicitud;
import py.edu.upe.hernandarias.entity.Usuario;

/**
 * Pedido del buzón tal como lo ve un administrador.
 *
 * @param usuarioId id de la cuenta con ese usuario, o {@code null} si no existe
 */
public record SolicitudResponse(
        Long id, TipoSolicitud tipo, String username, String nombre, String contacto, String mensaje,
        EstadoSolicitud estado, Instant fechaRegistro, String atendidaPor, Instant fechaAtencion,
        Long usuarioId, Rol usuarioRol, Boolean usuarioActivo, Boolean usuarioSuperAdmin) {

    public static SolicitudResponse from(Solicitud s, Usuario usuario) {
        return new SolicitudResponse(s.getId(), s.getTipo(), s.getUsername(), s.getNombre(), s.getContacto(),
                s.getMensaje(), s.getEstado(), s.getFechaRegistro(), s.getAtendidaPor(), s.getFechaAtencion(),
                usuario == null ? null : usuario.getId(),
                usuario == null ? null : usuario.getRol(),
                usuario == null ? null : usuario.isActivo(),
                usuario == null ? null : usuario.isSuperAdmin());
    }
}
