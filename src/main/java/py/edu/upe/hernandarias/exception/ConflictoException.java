package py.edu.upe.hernandarias.exception;

/** La operación viola una regla de negocio o de unicidad (HTTP 409). */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
