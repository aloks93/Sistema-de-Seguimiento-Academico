package py.edu.upe.hernandarias.excel;

import java.util.List;

/**
 * Resultado de validar o importar una plantilla.
 *
 * @param aplicado      {@code true} si se guardaron los datos (sin errores y no era solo validación)
 * @param docentes      docentes nuevos (creados, o a crear si solo se validó)
 * @param materias      materias nuevas
 * @param estudiantes   estudiantes nuevos
 * @param omitidos      filas que ya existían y no se tocaron
 * @param errores       filas con errores (con al menos uno no se guarda nada)
 * @param observaciones detalle por fila de errores y omitidos
 */
public record ResultadoImportacion(
        String carrera,
        boolean aplicado,
        int docentes,
        int materias,
        int estudiantes,
        int omitidos,
        int errores,
        List<Observacion> observaciones) {

    /**
     * @param tipo {@code ERROR} u {@code OMITIDO}
     * @param fila número de fila tal como se ve en Excel
     */
    public record Observacion(String tipo, String hoja, int fila, String mensaje) {
    }
}
