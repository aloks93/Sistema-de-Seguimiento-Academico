package py.edu.upe.hernandarias.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import py.edu.upe.hernandarias.exception.DemasiadosIntentosException;

/**
 * Freno contra llenar el buzón desde la pantalla de inicio de sesión (que no pide estar logueado):
 * cada dirección IP puede enviar hasta {@value #MAXIMO} pedidos cada 15 minutos.
 */
@Component
public class LimiteSolicitudes {

    static final int MAXIMO = 5;
    private static final Duration VENTANA = Duration.ofMinutes(15);

    private record Registro(int enviados, Instant desde) {
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    /** Cuenta un envío desde {@code origen}; lanza 429 si superó el máximo de la ventana. */
    public void registrar(String origen) {
        Instant ahora = Instant.now();
        Registro registro = registros.compute(origen, (clave, anterior) ->
                anterior == null || ahora.isAfter(anterior.desde().plus(VENTANA))
                        ? new Registro(1, ahora)
                        : new Registro(anterior.enviados() + 1, anterior.desde()));
        if (registro.enviados() > MAXIMO) {
            throw new DemasiadosIntentosException(
                    "Ya envió varios pedidos. Espere unos minutos o comuníquese con la secretaría.");
        }
        if (registros.size() > 10_000) {
            registros.values().removeIf(r -> ahora.isAfter(r.desde().plus(VENTANA)));
        }
    }

    public void limpiar() {
        registros.clear();
    }
}
