package py.edu.upe.hernandarias.exception;

/** Un dato enviado es incorrecto aunque tenga un formato válido (HTTP 400). */
public class DatoInvalidoException extends RuntimeException {

    public DatoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
