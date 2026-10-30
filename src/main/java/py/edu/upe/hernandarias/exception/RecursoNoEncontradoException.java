package py.edu.upe.hernandarias.exception;

/** El recurso solicitado no existe (HTTP 404). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String recurso, Object id) {
        super("%s no encontrado: %s".formatted(recurso, id));
    }
}
