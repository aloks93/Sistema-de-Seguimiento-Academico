package py.edu.upe.hernandarias.exception;

/** El código de verificación en dos pasos no es correcto (HTTP 401). */
public class CodigoInvalidoException extends RuntimeException {

    public CodigoInvalidoException() {
        super("Código de verificación incorrecto");
    }
}
