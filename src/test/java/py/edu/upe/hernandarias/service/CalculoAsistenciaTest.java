package py.edu.upe.hernandarias.service;

import static org.assertj.core.api.Assertions.assertThat;
import static py.edu.upe.hernandarias.entity.EstadoAsistencia.AUSENTE;
import static py.edu.upe.hernandarias.entity.EstadoAsistencia.JUSTIFICADO;
import static py.edu.upe.hernandarias.entity.EstadoAsistencia.PRESENTE;
import static py.edu.upe.hernandarias.entity.EstadoAsistencia.TARDE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import py.edu.upe.hernandarias.entity.EstadoAsistencia;
import py.edu.upe.hernandarias.service.CalculoAsistencia.Condicion;
import py.edu.upe.hernandarias.service.CalculoAsistencia.Resultado;

class CalculoAsistenciaTest {

    @Test
    void tresTardanzasEquivalenAUnaAusencia() {
        Resultado r = CalculoAsistencia.calcular(List.of(TARDE, TARDE, TARDE, PRESENTE), 70, null, false);
        assertThat(r.ausenciasComputadas()).isEqualTo(1);
        assertThat(r.porcentaje()).isEqualTo(75.0);

        Resultado dos = CalculoAsistencia.calcular(List.of(TARDE, TARDE, PRESENTE, PRESENTE), 70, null, false);
        assertThat(dos.ausenciasComputadas()).isZero();
        assertThat(dos.porcentaje()).isEqualTo(100.0);
    }

    @Test
    void laJustificadaSigueContandoComoAusencia() {
        Resultado r = CalculoAsistencia.calcular(List.of(JUSTIFICADO, PRESENTE, PRESENTE, PRESENTE), 70, null, false);
        assertThat(r.justificados()).isEqualTo(1);
        assertThat(r.ausenciasComputadas()).isEqualTo(1);
        assertThat(r.porcentaje()).isEqualTo(75.0);
    }

    @Test
    void sinClasesPrevistasLaCondicionDependeDelPorcentajeActual() {
        assertThat(condicion(List.of(PRESENTE, PRESENTE, PRESENTE, PRESENTE, PRESENTE), null)).isEqualTo(Condicion.HABILITADO);
        // 75 %: llega al mínimo pero a menos de 10 puntos
        assertThat(condicion(List.of(PRESENTE, PRESENTE, PRESENTE, AUSENTE), null)).isEqualTo(Condicion.EN_RIESGO);
        // 50 %: debajo del mínimo
        assertThat(condicion(List.of(PRESENTE, AUSENTE), null)).isEqualTo(Condicion.NO_HABILITADO);
        assertThat(condicion(List.of(), null)).isEqualTo(Condicion.SIN_REGISTROS);
    }

    @Test
    void conClasesPrevistasCalculaLasFaltasQueLeQuedan() {
        // 30 clases al 70 %: se permiten 9 ausencias
        Resultado tres = CalculoAsistencia.calcular(repetir(AUSENTE, 3), 70, 30, false);
        assertThat(tres.faltasRestantes()).isEqualTo(6);
        assertThat(tres.condicion()).isEqualTo(Condicion.HABILITADO);

        Resultado siete = CalculoAsistencia.calcular(repetir(AUSENTE, 7), 70, 30, false);
        assertThat(siete.faltasRestantes()).isEqualTo(2);
        assertThat(siete.condicion()).isEqualTo(Condicion.EN_RIESGO);

        Resultado diez = CalculoAsistencia.calcular(repetir(AUSENTE, 10), 70, 30, false);
        assertThat(diez.faltasRestantes()).isZero();
        assertThat(diez.condicion()).isEqualTo(Condicion.NO_HABILITADO);
        assertThat(diez.condicion().habilitadoParaFinal()).isFalse();
    }

    @Test
    void elExoneradoQuedaHabilitadoAunqueNoLlegueAlMinimo() {
        Resultado r = CalculoAsistencia.calcular(List.of(AUSENTE, AUSENTE, PRESENTE), 70, null, true);
        assertThat(r.porcentaje()).isEqualTo(33.3);
        assertThat(r.condicion()).isEqualTo(Condicion.EXONERADO);
        assertThat(r.condicion().habilitadoParaFinal()).isTrue();
    }

    @Test
    void conMinimoAltoLaAsistenciaPerfectaNoQuedaEnRiesgo() {
        assertThat(condicion(repetir(PRESENTE, 4), null, 95)).isEqualTo(Condicion.HABILITADO);
    }

    private static Condicion condicion(List<EstadoAsistencia> estados, Integer previstas) {
        return condicion(estados, previstas, 70);
    }

    private static Condicion condicion(List<EstadoAsistencia> estados, Integer previstas, int minimo) {
        return CalculoAsistencia.calcular(estados, minimo, previstas, false).condicion();
    }

    private static List<EstadoAsistencia> repetir(EstadoAsistencia estado, int veces) {
        return new ArrayList<>(Collections.nCopies(veces, estado));
    }
}
