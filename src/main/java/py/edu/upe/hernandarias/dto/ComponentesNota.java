package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Calificacion;
import py.edu.upe.hernandarias.exception.DatoInvalidoException;

/** Lo que se envía al cargar una nota: la nota única o sus componentes. */
public record ComponentesNota(Integer nota, Integer primerParcial, Integer segundoParcial,
                              Integer trabajoPractico, Integer examenFinal) {

    public boolean desglosada() {
        return primerParcial != null || segundoParcial != null || trabajoPractico != null || examenFinal != null;
    }

    /**
     * Copia los valores a la calificación: con componentes, la nota pasa a ser su suma;
     * con nota única, se borran los componentes.
     */
    public void aplicarA(Calificacion calificacion) {
        if (desglosada()) {
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

    private static int valor(Integer puntaje) {
        return puntaje == null ? 0 : puntaje;
    }
}
