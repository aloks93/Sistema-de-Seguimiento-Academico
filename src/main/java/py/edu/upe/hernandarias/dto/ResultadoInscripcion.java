package py.edu.upe.hernandarias.dto;

/**
 * @param inscriptos   inscripciones nuevas
 * @param yaInscriptos estudiantes que ya estaban inscriptos (se omiten)
 */
public record ResultadoInscripcion(int inscriptos, int yaInscriptos) {
}
