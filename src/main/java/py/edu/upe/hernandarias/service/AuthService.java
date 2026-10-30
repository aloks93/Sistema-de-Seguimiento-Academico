package py.edu.upe.hernandarias.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import py.edu.upe.hernandarias.dto.LoginRequest;
import py.edu.upe.hernandarias.dto.LoginResponse;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.exception.CodigoInvalidoException;
import py.edu.upe.hernandarias.repository.UsuarioRepository;
import py.edu.upe.hernandarias.security.JwtService;
import py.edu.upe.hernandarias.security.LimiteIntentos;
import py.edu.upe.hernandarias.security.Totp;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final Totp totp;
    private final LimiteIntentos limiteIntentos;

    /**
     * Verifica las credenciales (BCrypt) y emite un JWT. Si el usuario tiene verificación en dos
     * pasos y no envió código, responde {@code requiereCodigo = true} sin token.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        Usuario usuario = usuarioRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));

        if (usuario.tieneDosFactores()) {
            if (!StringUtils.hasText(request.codigo())) {
                return LoginResponse.faltaCodigo(usuario.getUsername());
            }
            limiteIntentos.verificarPermitido(usuario.getUsername());
            if (!totp.verificar(usuario.getTotpSecreto(), request.codigo().trim())) {
                limiteIntentos.registrarFallo(usuario.getUsername());
                throw new CodigoInvalidoException();
            }
            limiteIntentos.limpiar(usuario.getUsername());
        }

        return LoginResponse.bearer(
                jwtService.generarToken(usuario),
                jwtService.getExpiracionSegundos(),
                usuario.getUsername(),
                usuario.getRol());
    }
}
