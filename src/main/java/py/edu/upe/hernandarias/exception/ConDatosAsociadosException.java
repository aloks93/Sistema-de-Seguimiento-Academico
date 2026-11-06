package py.edu.upe.hernandarias.exception;

/**
 * No se puede eliminar porque tiene datos asociados (notas, asistencia, materias a cargo), pero un
 * ADMIN puede repetir la operación con {@code ?forzar=true} para eliminarlos también (HTTP 409).
 */
public class ConDatosAsociadosException extends ConflictoException {

    public ConDatosAsociadosException(String mensaje) {
        super(mensaje);
    }
}
