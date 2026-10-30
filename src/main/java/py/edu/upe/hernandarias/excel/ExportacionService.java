package py.edu.upe.hernandarias.excel;

import static py.edu.upe.hernandarias.excel.ExcelUtil.nombreArchivo;
import static py.edu.upe.hernandarias.excel.ExcelUtil.tabla;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.dto.CalificacionResponse;
import py.edu.upe.hernandarias.dto.MateriaResponse;
import py.edu.upe.hernandarias.exception.OperacionNoPermitidaException;
import py.edu.upe.hernandarias.service.CalificacionService;
import py.edu.upe.hernandarias.service.CarreraService;
import py.edu.upe.hernandarias.service.DocenteService;
import py.edu.upe.hernandarias.service.EstudianteService;
import py.edu.upe.hernandarias.service.MateriaService;

/**
 * Exportaciones a Excel. Usa los mismos servicios que la API, así que cada usuario exporta
 * exactamente lo que puede ver (un docente, solo sus carreras y materias).
 */
@Service
@RequiredArgsConstructor
public class ExportacionService {

    private final EstudianteService estudianteService;
    private final MateriaService materiaService;
    private final DocenteService docenteService;
    private final CarreraService carreraService;
    private final CalificacionService calificacionService;

    @Transactional(readOnly = true)
    public ArchivoExcel estudiantes(Long carreraId, String username) {
        List<List<Object>> filas = estudianteService.listar(carreraId, username).stream()
                .map(e -> List.<Object>of(e.nombreCompleto(), e.matricula(), e.carreraNombre(), e.username(),
                        e.activo() ? "Activo" : "Inactivo"))
                .toList();
        return new ArchivoExcel(nombreArchivo("estudiantes"), tabla("Estudiantes", "Estudiantes",
                List.of("Nombre completo", "Matrícula", "Carrera", "Usuario", "Estado"), filas));
    }

    @Transactional(readOnly = true)
    public ArchivoExcel materias(Long carreraId, String username) {
        List<List<Object>> filas = materiaService.listar(carreraId, username).stream()
                .map(m -> List.<Object>of(m.codigo(), m.nombre(), m.carreraNombre(), m.semestre(), m.creditos(),
                        m.docenteNombre() == null ? "Sin asignar" : m.docenteNombre()))
                .toList();
        return new ArchivoExcel(nombreArchivo("materias"), tabla("Materias", "Materias",
                List.of("Código", "Materia", "Carrera", "Semestre", "Créditos", "Docente"), filas));
    }

    @Transactional(readOnly = true)
    public ArchivoExcel docentes() {
        List<List<Object>> filas = docenteService.listar().stream()
                .map(d -> List.<Object>of(d.nombreCompleto(), d.username(),
                        d.materias().isEmpty() ? "Ninguna" : String.join(", ", d.materias()),
                        d.activo() ? "Activo" : "Inactivo"))
                .toList();
        return new ArchivoExcel(nombreArchivo("docentes"), tabla("Docentes", "Docentes",
                List.of("Nombre completo", "Usuario", "Materias a cargo", "Estado"), filas));
    }

    @Transactional(readOnly = true)
    public ArchivoExcel carreras(String username) {
        List<List<Object>> filas = carreraService.listar(username).stream()
                .map(c -> List.<Object>of(c.codigo(), c.nombre(), c.semestres(), c.materias(), c.estudiantes()))
                .toList();
        return new ArchivoExcel(nombreArchivo("carreras"), tabla("Carreras", "Carreras",
                List.of("Código", "Carrera", "Semestres", "Materias", "Estudiantes"), filas));
    }

    /** Planilla de una materia: todos los estudiantes de su carrera, con su nota si la tienen. */
    @Transactional(readOnly = true)
    public ArchivoExcel planillaDeMateria(Long materiaId, String username) {
        MateriaResponse materia = materiaService.listar(null, username).stream()
                .filter(m -> m.id().equals(materiaId))
                .findFirst()
                .orElseThrow(() -> new OperacionNoPermitidaException("No tiene acceso a esa materia"));

        Map<Long, CalificacionResponse> notas = calificacionService.listar(null, materiaId, username).stream()
                .collect(Collectors.toMap(CalificacionResponse::estudianteId, Function.identity()));

        List<List<Object>> filas = new ArrayList<>();
        estudianteService.listar(materia.carreraId(), username).forEach(estudiante -> {
            CalificacionResponse nota = notas.get(estudiante.id());
            filas.add(nota == null
                    ? List.of(estudiante.nombreCompleto(), estudiante.matricula(), "", "")
                    : List.of(estudiante.nombreCompleto(), estudiante.matricula(), nota.nota(), fecha(nota.fechaActualizacion())));
        });
        agregarPromedio(filas, notas.values().stream().map(CalificacionResponse::nota).toList(), 4, 2);

        String titulo = "Planilla de notas · %s %s · %s · %d° semestre".formatted(
                materia.codigo(), materia.nombre(), materia.carreraNombre(), materia.semestre());
        return new ArchivoExcel(nombreArchivo("notas-" + materia.codigo()), tabla("Notas", titulo,
                List.of("Estudiante", "Matrícula", "Nota", "Actualizada"), filas));
    }

    /** Todas las notas visibles para el usuario (todas para un ADMIN; las de sus materias para un docente). */
    @Transactional(readOnly = true)
    public ArchivoExcel notas(String username) {
        List<List<Object>> filas = calificacionService.listar(null, null, username).stream()
                .map(n -> List.<Object>of(n.nombreEstudiante(), n.matricula(), n.codigoMateria(), n.nombreMateria(),
                        n.semestre(), n.nota(), fecha(n.fechaActualizacion())))
                .toList();
        return new ArchivoExcel(nombreArchivo("notas"), tabla("Notas", "Notas cargadas",
                List.of("Estudiante", "Matrícula", "Código", "Materia", "Semestre", "Nota", "Actualizada"), filas));
    }

    /** Boletín del estudiante autenticado. */
    @Transactional(readOnly = true)
    public ArchivoExcel misNotas(String username) {
        List<CalificacionResponse> notas = calificacionService.listarPorUsuario(username);
        List<List<Object>> filas = new ArrayList<>(notas.stream()
                .map(n -> List.<Object>of(n.nombreMateria(), n.codigoMateria(),
                        n.docenteNombre() == null ? "" : n.docenteNombre(), n.semestre(), n.creditos(), n.nota()))
                .toList());
        agregarPromedio(filas, notas.stream().map(CalificacionResponse::nota).toList(), 6, 5);

        String titulo = notas.isEmpty() ? "Mis notas" : "Notas de " + notas.get(0).nombreEstudiante()
                + " · Matrícula " + notas.get(0).matricula();
        return new ArchivoExcel(nombreArchivo("mis-notas"), tabla("Notas", titulo,
                List.of("Materia", "Código", "Docente", "Semestre", "Créditos", "Nota"), filas));
    }

    /** Fila final "Promedio" con el valor en la columna indicada. */
    private static void agregarPromedio(List<List<Object>> filas, List<Integer> notas, int columnas, int columnaValor) {
        if (notas.isEmpty()) {
            return;
        }
        double promedio = notas.stream().mapToInt(Integer::intValue).average().orElse(0);
        List<Object> fila = new ArrayList<>();
        for (int i = 0; i < columnas; i++) {
            fila.add(i == 0 ? "Promedio" : i == columnaValor ? Math.round(promedio * 10) / 10.0 : "");
        }
        filas.add(fila);
    }

    private static String fecha(Instant instante) {
        return instante == null ? "" : ExcelUtil.fecha(LocalDate.ofInstant(instante, ZoneId.systemDefault()));
    }
}
