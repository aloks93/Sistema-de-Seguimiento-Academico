package py.edu.upe.hernandarias.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import py.edu.upe.hernandarias.dto.RestablecerRequest;
import py.edu.upe.hernandarias.dto.SolicitudRequest;
import py.edu.upe.hernandarias.dto.SolicitudResponse;
import py.edu.upe.hernandarias.dto.UsuarioUpdateRequest;
import py.edu.upe.hernandarias.entity.EstadoSolicitud;
import py.edu.upe.hernandarias.entity.Solicitud;
import py.edu.upe.hernandarias.entity.TipoSolicitud;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.DatoInvalidoException;
import py.edu.upe.hernandarias.exception.DemasiadosIntentosException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.SolicitudRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

/**
 * Buzón de los administradores: pedidos de "olvidé mi contraseña" y de acceso (cuenta nueva)
 * enviados desde la pantalla de inicio de sesión.
 */
@Service
@RequiredArgsConstructor
public class SolicitudService {

    /** Tope de pedidos pendientes: más allá se rechazan para que nadie llene la base de datos. */
    static final int MAXIMO_PENDIENTES = 500;

    private final SolicitudRepository solicitudRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioService usuarioService;

    /**
     * Registra el pedido. No informa si el usuario existe (para no revelar qué cuentas hay): la
     * respuesta es la misma en todos los casos. Un pedido pendiente repetido se actualiza en vez de duplicarse.
     */
    @Transactional
    public void enviar(SolicitudRequest request) {
        String username = limpiar(request.username());
        String nombre = limpiar(request.nombre());
        if (request.tipo() == TipoSolicitud.CONTRASENA && username == null) {
            throw new DatoInvalidoException("Escriba su usuario para que el administrador sepa qué cuenta restablecer");
        }
        if (request.tipo() == TipoSolicitud.ACCESO && nombre == null) {
            throw new DatoInvalidoException("Escriba su nombre completo");
        }

        Solicitud solicitud = username == null ? null : solicitudRepository
                .findFirstByTipoAndUsernameIgnoreCaseAndEstado(request.tipo(), username, EstadoSolicitud.PENDIENTE)
                .orElse(null);
        if (solicitud == null) {
            if (solicitudRepository.countByEstado(EstadoSolicitud.PENDIENTE) >= MAXIMO_PENDIENTES) {
                throw new DemasiadosIntentosException(
                        "El buzón está lleno en este momento. Comuníquese con la secretaría.");
            }
            solicitud = Solicitud.builder().tipo(request.tipo()).username(username).build();
        }
        solicitud.setNombre(nombre);
        solicitud.setContacto(limpiar(request.contacto()));
        solicitud.setMensaje(limpiar(request.mensaje()));
        solicitud.setFechaRegistro(Instant.now());
        solicitudRepository.save(solicitud);
    }

    /** Pedidos (opcionalmente de un estado), los más nuevos primero, con la cuenta a la que se refieren. */
    @Transactional(readOnly = true)
    public List<SolicitudResponse> listar(EstadoSolicitud estado) {
        List<Solicitud> solicitudes = estado == null
                ? solicitudRepository.findAllByOrderByFechaRegistroDesc()
                : solicitudRepository.findByEstadoOrderByFechaRegistroDesc(estado);
        Map<String, Usuario> usuarios = usuarioRepository.findAll().stream()
                .collect(Collectors.toMap(u -> u.getUsername().toLowerCase(), Function.identity(), (a, b) -> a));
        return solicitudes.stream()
                .map(s -> SolicitudResponse.from(s, s.getUsername() == null ? null : usuarios.get(s.getUsername().toLowerCase())))
                .toList();
    }

    @Transactional(readOnly = true)
    public long contarPendientes() {
        return solicitudRepository.countByEstado(EstadoSolicitud.PENDIENTE);
    }

    @Transactional
    public SolicitudResponse cambiarEstado(Long id, EstadoSolicitud estado, String usernameActual) {
        Solicitud solicitud = buscar(id);
        marcar(solicitud, estado, usernameActual);
        return SolicitudResponse.from(solicitud, cuentaDe(solicitud));
    }

    /**
     * Pone una contraseña nueva a la cuenta del pedido y lo marca como resuelto. Respeta la jerarquía
     * de administradores (ver UsuarioService#verificarPuedeGestionar).
     */
    @Transactional
    public SolicitudResponse restablecer(Long id, RestablecerRequest request, String usernameActual) {
        Solicitud solicitud = buscar(id);
        Usuario usuario = cuentaDe(solicitud);
        if (usuario == null) {
            throw new ConflictoException("No existe ninguna cuenta con el usuario \"%s\"".formatted(solicitud.getUsername()));
        }
        usuarioService.actualizar(usuario.getId(), new UsuarioUpdateRequest(
                Boolean.TRUE.equals(request.activar()) ? Boolean.TRUE : null,
                request.password(),
                request.quitarDosFactores()), usernameActual);
        marcar(solicitud, EstadoSolicitud.RESUELTA, usernameActual);
        return SolicitudResponse.from(solicitud, usuario);
    }

    @Transactional
    public void eliminar(Long id) {
        solicitudRepository.delete(buscar(id));
    }

    private void marcar(Solicitud solicitud, EstadoSolicitud estado, String usernameActual) {
        solicitud.setEstado(estado);
        boolean pendiente = estado == EstadoSolicitud.PENDIENTE;
        solicitud.setAtendidaPor(pendiente ? null : usernameActual);
        solicitud.setFechaAtencion(pendiente ? null : Instant.now());
        solicitudRepository.save(solicitud);
    }

    private Usuario cuentaDe(Solicitud solicitud) {
        if (solicitud.getUsername() == null) {
            return null;
        }
        return usuarioRepository.findFirstByUsernameIgnoreCase(solicitud.getUsername()).orElse(null);
    }

    private Solicitud buscar(Long id) {
        return solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Solicitud", id));
    }

    private static String limpiar(String texto) {
        return StringUtils.hasText(texto) ? texto.trim() : null;
    }
}
