package py.edu.upe.hernandarias.exception;

/** El usuario tiene el rol adecuado pero no puede operar sobre ese recurso concreto (HTTP 403). */
public class OperacionNoPermitidaException extends RuntimeException {

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
