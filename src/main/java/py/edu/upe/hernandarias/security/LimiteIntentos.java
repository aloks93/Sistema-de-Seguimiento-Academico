package py.edu.upe.hernandarias.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import py.edu.upe.hernandarias.exception.DemasiadosIntentosException;

/**
 * Freno contra adivinar códigos de verificación: tras {@value #MAXIMO} códigos fallidos de un
 * mismo usuario se bloquea el ingreso de ese usuario durante 5 minutos.
 */
@Component
public class LimiteIntentos {

    static final int MAXIMO = 5;
    private static final Duration VENTANA = Duration.ofMinutes(5);

    private record Registro(int fallidos, Instant desde) {
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    public void verificarPermitido(String username) {
        Registro registro = registros.get(username);
        if (registro == null) {
            return;
        }
        if (Instant.now().isAfter(registro.desde().plus(VENTANA))) {
            registros.remove(username);
        } else if (registro.fallidos() >= MAXIMO) {
            throw new DemasiadosIntentosException(
                    "Demasiados códigos incorrectos. Espere 5 minutos e intente de nuevo.");
        }
    }

    public void registrarFallo(String username) {
        registros.merge(username, new Registro(1, Instant.now()),
                (anterior, nuevo) -> new Registro(anterior.fallidos() + 1, anterior.desde()));
    }

    public void limpiar(String username) {
        registros.remove(username);
    }
}
