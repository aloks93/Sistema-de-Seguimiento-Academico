package py.edu.upe.hernandarias;

import org.springframework.jdbc.core.JdbcTemplate;

/** Utilidades compartidas por los tests de integración. */
public final class BaseDePrueba {

    private BaseDePrueba() {
    }

    /**
     * Vacía las tablas de asistencia, inscripciones y exoneraciones, que apuntan a estudiantes y materias:
     * hay que llamarlo antes de borrar esas tablas.
     */
    public static void vaciarAsistenciaEInscripciones(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM asistencias");
        jdbc.update("DELETE FROM clases");
        jdbc.update("DELETE FROM exoneraciones");
        jdbc.update("DELETE FROM inscripciones");
    }
}
