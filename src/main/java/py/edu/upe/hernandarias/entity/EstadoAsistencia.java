package py.edu.upe.hernandarias.entity;

/**
 * Cómo figura un estudiante en una clase. Según el reglamento de la UPE, tres llegadas tardías
 * equivalen a una ausencia y la ausencia justificada queda registrada pero no se anula.
 */
public enum EstadoAsistencia {
    PRESENTE,
    TARDE,
    AUSENTE,
    JUSTIFICADO
}
