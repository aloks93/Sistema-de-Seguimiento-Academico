package py.edu.upe.hernandarias.excel;

import static py.edu.upe.hernandarias.excel.ExcelUtil.nombreArchivo;
import static py.edu.upe.hernandarias.excel.ExcelUtil.tabla;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.dto.CalificacionResponse;
import py.edu.upe.hernandarias.dto.FilaAsistenciaResponse;
import py.edu.upe.hernandarias.dto.InscripcionResponse;
import py.edu.upe.hernandarias.dto.MateriaResponse;
import py.edu.upe.hernandarias.dto.ResumenAsistenciaResponse;
import py.edu.upe.hernandarias.entity.EstadoAsistencia;
import py.edu.upe.hernandarias.exception.OperacionNoPermitidaException;
import py.edu.upe.hernandarias.service.AsistenciaService;
import py.edu.upe.hernandarias.service.CalculoAsistencia;
import py.edu.upe.hernandarias.service.CalificacionService;
import py.edu.upe.hernandarias.service.CarreraService;
import py.edu.upe.hernandarias.service.DocenteService;
import py.edu.upe.hernandarias.service.EstudianteService;
import py.edu.upe.hernandarias.service.InscripcionService;
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
    private final InscripcionService inscripcionService;
    private final AsistenciaService asistenciaService;

    private static final Map<EstadoAsistencia, String> LETRA = new EnumMap<>(Map.of(
            EstadoAsistencia.PRESENTE, "P", EstadoAsistencia.TARDE, "T",
            EstadoAsistencia.AUSENTE, "A", EstadoAsistencia.JUSTIFICADO, "J"));

    private static final Map<CalculoAsistencia.Condicion, String> CONDICION = new EnumMap<>(Map.of(
            CalculoAsistencia.Condicion.HABILITADO, "Habilitado",
            CalculoAsistencia.Condicion.EN_RIESGO, "En riesgo",
            CalculoAsistencia.Condicion.NO_HABILITADO, "No habilitado",
            CalculoAsistencia.Condicion.EXONERADO, "Exonerado",
            CalculoAsistencia.Condicion.SIN_REGISTROS, "Sin registros"));

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

    /** Planilla de una materia: todos sus inscriptos, con la nota desglosada si la tienen. */
    @Transactional(readOnly = true)
    public ArchivoExcel planillaDeMateria(Long materiaId, String username) {
        MateriaResponse materia = materia(materiaId, username);
        Map<Long, CalificacionResponse> notas = calificacionService.listar(null, materiaId, username).stream()
                .collect(Collectors.toMap(CalificacionResponse::estudianteId, Function.identity()));

        List<List<Object>> filas = new ArrayList<>();
        for (InscripcionResponse inscripto : inscripcionService.listar(materiaId, username)) {
            CalificacionResponse nota = notas.get(inscripto.estudianteId());
            List<Object> fila = new ArrayList<>(List.of(inscripto.nombreCompleto(), inscripto.matricula()));
            if (nota == null) {
                fila.addAll(List.of("", "", "", "", "", "", ""));
            } else {
                fila.addAll(List.of(nota.nota(), valor(nota.calificacion()), valor(nota.primerParcial()),
                        valor(nota.segundoParcial()), valor(nota.trabajoPractico()), valor(nota.examenFinal()),
                        fecha(nota.fechaActualizacion())));
            }
            filas.add(fila);
        }
        agregarPromedio(filas, notas.values().stream().map(CalificacionResponse::nota).toList(), 9, 2);

        String titulo = "Planilla de notas · %s %s · %s · %d° semestre".formatted(
                materia.codigo(), materia.nombre(), materia.carreraNombre(), materia.semestre());
        return new ArchivoExcel(nombreArchivo("notas-" + materia.codigo()), tabla("Notas", titulo,
                List.of("Estudiante", "Matrícula", "Total (100)", "Calificación (1-5)",
                        "1er parcial (%d)".formatted(materia.puntajes().primerParcial()),
                        "2do parcial (%d)".formatted(materia.puntajes().segundoParcial()),
                        "Trabajo práctico (%d)".formatted(materia.puntajes().trabajoPractico()),
                        "Examen final (%d)".formatted(materia.puntajes().examenFinal()), "Actualizada"), filas));
    }

    /** Todas las notas visibles para el usuario (todas para un ADMIN; las de sus materias para un docente). */
    @Transactional(readOnly = true)
    public ArchivoExcel notas(String username) {
        List<List<Object>> filas = calificacionService.listar(null, null, username).stream()
                .map(n -> List.<Object>of(n.nombreEstudiante(), n.matricula(), n.codigoMateria(), n.nombreMateria(),
                        n.semestre(), n.nota(), valor(n.calificacion()), fecha(n.fechaActualizacion())))
                .toList();
        return new ArchivoExcel(nombreArchivo("notas"), tabla("Notas", "Notas cargadas",
                List.of("Estudiante", "Matrícula", "Código", "Materia", "Semestre", "Nota", "Calificación (1-5)",
                        "Actualizada"), filas));
    }

    /** Boletín del estudiante autenticado. */
    @Transactional(readOnly = true)
    public ArchivoExcel misNotas(String username) {
        List<CalificacionResponse> notas = calificacionService.listarPorUsuario(username);
        List<List<Object>> filas = new ArrayList<>(notas.stream()
                .map(n -> List.<Object>of(n.nombreMateria(), n.codigoMateria(),
                        n.docenteNombre() == null ? "" : n.docenteNombre(), n.semestre(), n.creditos(), n.nota(),
                        n.calificacion() == null ? "En curso" : n.calificacion()))
                .toList());
        agregarPromedio(filas, notas.stream().map(CalificacionResponse::nota).toList(), 7, 5);

        String titulo = notas.isEmpty() ? "Mis notas" : "Notas de " + notas.get(0).nombreEstudiante()
                + " · Matrícula " + notas.get(0).matricula();
        return new ArchivoExcel(nombreArchivo("mis-notas"), tabla("Notas", titulo,
                List.of("Materia", "Código", "Docente", "Semestre", "Créditos", "Nota", "Calificación (1-5)"), filas));
    }

    // ======================= Asistencia =======================

    /** Planilla de asistencia: hoja "Resumen" (porcentaje y condición) y hoja "Clases" (P/T/A/J por fecha). */
    @Transactional(readOnly = true)
    public ArchivoExcel asistencia(Long materiaId, String username) {
        ResumenAsistenciaResponse resumen = asistenciaService.resumen(materiaId, username);
        AsistenciaService.Matriz matriz = asistenciaService.matriz(materiaId, username);
        String materia = resumen.codigoMateria() + " " + resumen.nombreMateria();
        String reglas = "Mínimo %d %% · %d clases dictadas%s · 3 tardanzas = 1 ausencia · la justificada cuenta como ausencia"
                .formatted(resumen.asistenciaMinima(), resumen.clasesDictadas(),
                        resumen.clasesPrevistas() == null ? "" : " de " + resumen.clasesPrevistas() + " previstas");

        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            ExcelUtil.Estilos estilos = ExcelUtil.estilos(libro);

            List<String> columnas = List.of("Estudiante", "Matrícula", "Presentes", "Tardanzas", "Ausentes",
                    "Justificadas", "Ausencias computadas", "Asistencia (%)", "Faltas restantes", "Condición");
            Sheet hoja = libro.createSheet("Resumen");
            ExcelUtil.encabezado(hoja, estilos, "Asistencia · " + materia, reglas, columnas);
            int indice = ExcelUtil.PRIMERA_FILA_DATOS;
            for (FilaAsistenciaResponse f : resumen.estudiantes()) {
                ExcelUtil.fila(hoja, indice++, List.of(f.nombreEstudiante(), f.matricula(), f.presentes(), f.tardes(),
                        f.ausentes(), f.justificados(), f.ausenciasComputadas(), valor(f.porcentaje()),
                        valor(f.faltasRestantes()), CONDICION.get(f.condicion())), estilos.texto());
            }
            filtro(hoja, indice, columnas.size());
            ExcelUtil.ajustarColumnas(hoja, columnas.size());

            List<String> columnasClases = new ArrayList<>(List.of("Estudiante", "Matrícula"));
            matriz.fechas().forEach(dia -> columnasClases.add(ExcelUtil.fecha(dia).substring(0, 5)));
            Sheet clases = libro.createSheet("Clases");
            ExcelUtil.encabezado(clases, estilos, "Clases · " + materia,
                    "P = presente · T = tarde · A = ausente · J = justificada", columnasClases);
            indice = ExcelUtil.PRIMERA_FILA_DATOS;
            for (FilaAsistenciaResponse f : resumen.estudiantes()) {
                List<Object> fila = new ArrayList<>(List.of(f.nombreEstudiante(), f.matricula()));
                matriz.estados().getOrDefault(f.estudianteId(), List.of())
                        .forEach(estado -> fila.add(estado == null ? "" : LETRA.get(estado)));
                ExcelUtil.fila(clases, indice++, fila, estilos.texto());
            }
            filtro(clases, indice, columnasClases.size());
            ExcelUtil.ajustarColumnas(clases, 2);
            for (int i = 2; i < columnasClases.size(); i++) {
                clases.setColumnWidth(i, 7 * 256);
            }
            return new ArchivoExcel(nombreArchivo("asistencia-" + resumen.codigoMateria()), ExcelUtil.aBytes(libro));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Lista de habilitados para el examen final: primero los habilitados, después los que no. */
    @Transactional(readOnly = true)
    public ArchivoExcel habilitados(Long materiaId, String username) {
        ResumenAsistenciaResponse resumen = asistenciaService.resumen(materiaId, username);
        List<FilaAsistenciaResponse> ordenados = resumen.estudiantes().stream()
                .sorted(Comparator.comparing((FilaAsistenciaResponse f) -> !f.condicion().habilitadoParaFinal())
                        .thenComparing(FilaAsistenciaResponse::nombreEstudiante))
                .toList();
        List<List<Object>> filas = new ArrayList<>();
        int numero = 1;
        for (FilaAsistenciaResponse f : ordenados) {
            String condicion = switch (f.condicion()) {
                case NO_HABILITADO -> "NO HABILITADO";
                case EXONERADO -> "Habilitado (exonerado de asistencia)";
                case SIN_REGISTROS -> "Habilitado (sin asistencia registrada)";
                default -> "Habilitado";
            };
            filas.add(List.of(numero++, f.nombreEstudiante(), f.matricula(),
                    f.porcentaje() == null ? "" : f.porcentaje() + " %", condicion));
        }
        String titulo = "Habilitados para el examen final · %s %s · mínimo %d %% de asistencia".formatted(
                resumen.codigoMateria(), resumen.nombreMateria(), resumen.asistenciaMinima());
        return new ArchivoExcel(nombreArchivo("habilitados-" + resumen.codigoMateria()), tabla("Habilitados", titulo,
                List.of("N°", "Estudiante", "Matrícula", "Asistencia", "Condición"), filas));
    }

    private MateriaResponse materia(Long materiaId, String username) {
        return materiaService.listar(null, username).stream()
                .filter(m -> m.id().equals(materiaId))
                .findFirst()
                .orElseThrow(() -> new OperacionNoPermitidaException("No tiene acceso a esa materia"));
    }

    private static void filtro(Sheet hoja, int siguienteFila, int columnas) {
        if (siguienteFila > ExcelUtil.PRIMERA_FILA_DATOS) {
            hoja.setAutoFilter(new CellRangeAddress(ExcelUtil.FILA_ENCABEZADO, siguienteFila - 1, 0, columnas - 1));
        }
    }

    /** Número o celda vacía. */
    private static Object valor(Object numero) {
        return numero == null ? "" : numero;
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
