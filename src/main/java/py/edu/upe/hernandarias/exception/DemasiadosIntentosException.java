package py.edu.upe.hernandarias.exception;

/** Demasiados intentos fallidos seguidos (HTTP 429). */
public class DemasiadosIntentosException extends RuntimeException {

    public DemasiadosIntentosException(String mensaje) {
        super(mensaje);
    }
}
