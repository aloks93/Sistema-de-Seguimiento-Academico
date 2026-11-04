package py.edu.upe.hernandarias.service;

import java.util.Collection;
import py.edu.upe.hernandarias.entity.EstadoAsistencia;

/**
 * Reglas de asistencia del reglamento de la UPE:
 * <ul>
 *   <li>tres llegadas tardías equivalen a una ausencia;</li>
 *   <li>la ausencia justificada queda registrada pero sigue contando como ausencia;</li>
 *   <li>quien no alcanza el mínimo (70 % por defecto) no tiene derecho al examen final;</li>
 *   <li>un estudiante exonerado queda habilitado aunque no llegue al mínimo.</li>
 * </ul>
 * El porcentaje se calcula sobre las clases en las que el estudiante figura (si se inscribió tarde,
 * las clases anteriores no le cuentan). Con las clases previstas de la materia se calcula además
 * cuántas faltas le quedan antes de perder el derecho al examen.
 */
public final class CalculoAsistencia {

    public static final int TARDANZAS_POR_AUSENCIA = 3;
    /** Con previstas: "en riesgo" si le quedan esta cantidad de faltas o menos. */
    public static final int FALTAS_DE_MARGEN = 2;
    /** Sin previstas: "en riesgo" si está a menos de estos puntos del mínimo. */
    public static final int PUNTOS_DE_MARGEN = 10;

    private CalculoAsistencia() {
    }

    public enum Condicion {
        HABILITADO,
        EN_RIESGO,
        NO_HABILITADO,
        EXONERADO,
        /** Todavía no figura en ninguna clase. */
        SIN_REGISTROS;

        public boolean habilitadoParaFinal() {
            return this != NO_HABILITADO;
        }
    }

    /**
     * @param porcentaje      asistencia efectiva con un decimal; {@code null} sin clases registradas
     * @param faltasRestantes faltas que todavía puede tener; {@code null} si la materia no tiene clases previstas
     */
    public record Resultado(int presentes, int tardes, int ausentes, int justificados, int registradas,
                            int ausenciasComputadas, Double porcentaje, Integer faltasRestantes, Condicion condicion) {
    }

    public static Resultado calcular(Collection<EstadoAsistencia> estados, int minimo, Integer clasesPrevistas,
                                     boolean exonerado) {
        int presentes = 0;
        int tardes = 0;
        int ausentes = 0;
        int justificados = 0;
        for (EstadoAsistencia estado : estados) {
            switch (estado) {
                case PRESENTE -> presentes++;
                case TARDE -> tardes++;
                case AUSENTE -> ausentes++;
                case JUSTIFICADO -> justificados++;
            }
        }
        int registradas = estados.size();
        int ausenciasComputadas = ausentes + justificados + tardes / TARDANZAS_POR_AUSENCIA;
        Double porcentaje = registradas == 0
                ? null
                : Math.round((registradas - ausenciasComputadas) * 1000.0 / registradas) / 10.0;

        Integer faltasRestantes = null;
        Condicion condicion;
        if (clasesPrevistas != null) {
            int total = Math.max(clasesPrevistas, registradas);
            int permitidas = total * (100 - minimo) / 100;
            int restantes = permitidas - ausenciasComputadas;
            faltasRestantes = Math.max(restantes, 0);
            if (restantes < 0) {
                condicion = Condicion.NO_HABILITADO;
            } else if (registradas == 0) {
                condicion = Condicion.SIN_REGISTROS;
            } else if (restantes <= FALTAS_DE_MARGEN) {
                condicion = Condicion.EN_RIESGO;
            } else {
                condicion = Condicion.HABILITADO;
            }
        } else if (porcentaje == null) {
            condicion = Condicion.SIN_REGISTROS;
        } else if (porcentaje < minimo) {
            condicion = Condicion.NO_HABILITADO;
        } else if (porcentaje < Math.min(minimo + PUNTOS_DE_MARGEN, 100)) {
            condicion = Condicion.EN_RIESGO;
        } else {
            condicion = Condicion.HABILITADO;
        }

        if (exonerado) {
            condicion = Condicion.EXONERADO;
        }
        return new Resultado(presentes, tardes, ausentes, justificados, registradas, ausenciasComputadas,
                porcentaje, faltasRestantes, condicion);
    }
}
