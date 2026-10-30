package py.edu.upe.hernandarias.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.dto.CambioContrasenaRequest;
import py.edu.upe.hernandarias.dto.PerfilResponse;
import py.edu.upe.hernandarias.dto.PreferenciasDto;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.exception.DatoInvalidoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

/** Datos y ajustes del usuario autenticado. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final DocenteRepository docenteRepository;
    private final EstudianteRepository estudianteRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PerfilResponse obtener(String username) {
        Usuario usuario = buscar(username);
        String nombre = switch (usuario.getRol()) {
            case DOCENTE -> docenteRepository.findByUsuarioUsername(username)
                    .map(Docente::getNombreCompleto).orElse(username);
            case ESTUDIANTE -> estudianteRepository.findByUsuarioUsername(username)
                    .map(Estudiante::getNombreCompleto).orElse(username);
            case ADMIN -> username;
        };
        return new PerfilResponse(usuario.getUsername(), usuario.getRol(), nombre, leer(usuario.getPreferencias()));
    }

    @Transactional
    public PreferenciasDto guardarPreferencias(String username, PreferenciasDto preferencias) {
        Usuario usuario = buscar(username);
        try {
            usuario.setPreferencias(objectMapper.writeValueAsString(preferencias));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudieron serializar las preferencias", e);
        }
        usuarioRepository.save(usuario);
        return preferencias;
    }

    @Transactional
    public void cambiarContrasena(String username, CambioContrasenaRequest request) {
        Usuario usuario = buscar(username);
        if (!passwordEncoder.matches(request.actual(), usuario.getPasswordHash())) {
            throw new DatoInvalidoException("La contraseña actual no es correcta");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.nueva()));
        usuarioRepository.save(usuario);
    }

    private PreferenciasDto leer(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PreferenciasDto.class);
        } catch (JsonProcessingException e) {
            // Preferencias de una versión anterior o dañadas: se usan las de por defecto
            log.warn("Preferencias ilegibles, se ignoran: {}", e.getOriginalMessage());
            return null;
        }
    }

    private Usuario buscar(String username) {
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", username));
    }
}
