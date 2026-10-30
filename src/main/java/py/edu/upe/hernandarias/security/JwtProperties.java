package py.edu.upe.hernandarias.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración de JWT ({@code app.jwt.*}).
 *
 * @param secret     clave HMAC codificada en Base64 (mínimo 256 bits)
 * @param expiration tiempo de vida del token (formato ISO-8601, ej. {@code PT8H})
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration expiration) {
}
