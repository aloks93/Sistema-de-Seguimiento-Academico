package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Calificacion;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.exception.DatoInvalidoException;

/** Lo que se envía al cargar una nota: la nota única o sus componentes. */
public record ComponentesNota(Integer nota, Integer primerParcial, Integer segundoParcial,
                              Integer trabajoPractico, Integer examenFinal) {

    public boolean desglosada() {
        return primerParcial != null || segundoParcial != null || trabajoPractico != null || examenFinal != null;
    }

    /**
     * Copia los valores a la calificación: con componentes, la nota pasa a ser su suma;
     * con nota única, se borran los componentes. Cada componente no puede superar
     * lo que vale en la materia de la calificación.
     */
    public void aplicarA(Calificacion calificacion) {
        if (desglosada()) {
            Materia materia = calificacion.getMateria();
            verificarMaximo("1er parcial", primerParcial, materia.getPuntajePrimerParcial(), materia);
            verificarMaximo("2do parcial", segundoParcial, materia.getPuntajeSegundoParcial(), materia);
            verificarMaximo("trabajo práctico", trabajoPractico, materia.getPuntajeTrabajoPractico(), materia);
            verificarMaximo("examen final", examenFinal, materia.getPuntajeExamenFinal(), materia);
            calificacion.setPrimerParcial(primerParcial);
            calificacion.setSegundoParcial(segundoParcial);
            calificacion.setTrabajoPractico(trabajoPractico);
            calificacion.setExamenFinal(examenFinal);
            calificacion.setNota(valor(primerParcial) + valor(segundoParcial) + valor(trabajoPractico) + valor(examenFinal));
        } else if (nota != null) {
            calificacion.setPrimerParcial(null);
            calificacion.setSegundoParcial(null);
            calificacion.setTrabajoPractico(null);
            calificacion.setExamenFinal(null);
            calificacion.setNota(nota);
        } else {
            throw new DatoInvalidoException("Indique la nota o al menos uno de sus componentes");
        }
    }

    private static void verificarMaximo(String componente, Integer puntaje, int maximo, Materia materia) {
        if (puntaje != null && puntaje > maximo) {
            throw new DatoInvalidoException("En %s el %s vale %d puntos; no se pueden cargar %d"
                    .formatted(materia.getCodigo(), componente, maximo, puntaje));
        }
    }

    private static int valor(Integer puntaje) {
        return puntaje == null ? 0 : puntaje;
    }
}
