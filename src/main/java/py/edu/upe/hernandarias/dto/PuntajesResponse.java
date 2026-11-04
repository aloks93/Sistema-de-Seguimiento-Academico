package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Materia;

/** Cuánto vale cada componente de la nota en una materia (suman 100). */
public record PuntajesResponse(int primerParcial, int segundoParcial, int trabajoPractico, int examenFinal) {

    public static PuntajesResponse from(Materia materia) {
        return new PuntajesResponse(materia.getPuntajePrimerParcial(), materia.getPuntajeSegundoParcial(),
                materia.getPuntajeTrabajoPractico(), materia.getPuntajeExamenFinal());
    }
}
