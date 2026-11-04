package py.edu.upe.hernandarias.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import py.edu.upe.hernandarias.dto.ClaseDetalleResponse;
import py.edu.upe.hernandarias.dto.ClaseRequest;
import py.edu.upe.hernandarias.dto.ClaseResponse;
import py.edu.upe.hernandarias.dto.ExoneracionRequest;
import py.edu.upe.hernandarias.dto.ExoneracionResponse;
import py.edu.upe.hernandarias.dto.FilaAsistenciaResponse;
import py.edu.upe.hernandarias.dto.MiAsistenciaResponse;
import py.edu.upe.hernandarias.dto.ReporteAsistenciaResponse;
import py.edu.upe.hernandarias.dto.ResumenAsistenciaResponse;
import py.edu.upe.hernandarias.entity.Asistencia;
import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.entity.Clase;
import py.edu.upe.hernandarias.entity.EstadoAsistencia;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Exoneracion;
import py.edu.upe.hernandarias.entity.Inscripcion;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.DatoInvalidoException;
import py.edu.upe.hernandarias.exception.OperacionNoPermitidaException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.AsistenciaRepository;
import py.edu.upe.hernandarias.repository.ClaseRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.ExoneracionRepository;
import py.edu.upe.hernandarias.repository.InscripcionRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;

/**
 * Asistencia: el docente a cargo (o un ADMIN) pasa lista en cada clase; el sistema calcula el
 * porcentaje de cada inscripto y su condición para el examen final (ver {@link CalculoAsistencia}).
 */
@Service
@RequiredArgsConstructor
public class AsistenciaService {

    /** Máximo de estudiantes en la lista de alertas del reporte. */
    private static final int MAXIMO_ALERTAS = 25;

    private final ClaseRepository claseRepository;
    private final AsistenciaRepository asistenciaRepository;
    private final InscripcionRepository inscripcionRepository;
    private final ExoneracionRepository exoneracionRepository;
    private final MateriaRepository materiaRepository;
    private final EstudianteRepository estudianteRepository;
    private final PermisosService permisosService;

    // ======================= Clases =======================

    @Transactional(readOnly = true)
    public List<ClaseResponse> listarClases(Long materiaId, String username) {
        materiaConPermiso(materiaId, username);
        Map<Long, List<Asistencia>> porClase = asistenciaRepository.findByClaseMateriaId(materiaId).stream()
                .collect(Collectors.groupingBy(asistencia -> asistencia.getClase().getId()));
        return claseRepository.findByMateriaIdOrderByFechaDesc(materiaId).stream()
                .map(clase -> ClaseResponse.from(clase, porClase.getOrDefault(clase.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClaseDetalleResponse detalle(Long claseId, String username) {
        return detalle(claseConPermiso(claseId, username));
    }

    @Transactional
    public ClaseDetalleResponse crear(ClaseRequest request, String username) {
        if (request.materiaId() == null) {
            throw new DatoInvalidoException("Indique la materia de la clase");
        }
        Materia materia = materiaConPermiso(request.materiaId(), username);
        validarFecha(request.fecha());
        if (claseRepository.existsByMateriaIdAndFecha(materia.getId(), request.fecha())) {
            throw new ConflictoException("Ya hay una clase de %s el %s; ábrala para corregirla"
                    .formatted(materia.getCodigo(), request.fecha()));
        }

        Clase clase = Clase.builder().materia(materia).fecha(request.fecha()).tema(texto(request.tema())).build();
        aplicarRegistros(clase, request.registros());
        return detalle(claseRepository.saveAndFlush(clase));
    }

    @Transactional
    public ClaseDetalleResponse actualizar(Long claseId, ClaseRequest request, String username) {
        Clase clase = claseConPermiso(claseId, username);
        validarFecha(request.fecha());
        if (claseRepository.existsByMateriaIdAndFechaAndIdNot(clase.getMateria().getId(), request.fecha(), claseId)) {
            throw new ConflictoException("Ya hay otra clase de %s el %s"
                    .formatted(clase.getMateria().getCodigo(), request.fecha()));
        }
        clase.setFecha(request.fecha());
        clase.setTema(texto(request.tema()));
        aplicarRegistros(clase, request.registros());
        return detalle(claseRepository.saveAndFlush(clase));
    }

    @Transactional
    public void eliminarClase(Long claseId, String username) {
        claseRepository.delete(claseConPermiso(claseId, username));
    }

    /** Crea, cambia o quita (estado null) el registro de cada estudiante indicado. */
    private void aplicarRegistros(Clase clase, List<ClaseRequest.Registro> registros) {
        Map<Long, Estudiante> inscriptos = inscriptos(clase.getMateria().getId()).stream()
                .collect(Collectors.toMap(Estudiante::getId, Function.identity()));
        Map<Long, Asistencia> actuales = clase.getAsistencias().stream()
                .collect(Collectors.toMap(asistencia -> asistencia.getEstudiante().getId(), Function.identity()));

        Set<Long> vistos = new HashSet<>();
        for (ClaseRequest.Registro registro : registros) {
            Long estudianteId = registro.estudianteId();
            if (!vistos.add(estudianteId)) {
                throw new DatoInvalidoException("El estudiante %d figura dos veces en la lista".formatted(estudianteId));
            }
            Estudiante estudiante = inscriptos.get(estudianteId);
            if (estudiante == null) {
                throw new DatoInvalidoException("El estudiante %d no está inscripto en %s"
                        .formatted(estudianteId, clase.getMateria().getCodigo()));
            }

            Asistencia actual = actuales.get(estudianteId);
            if (registro.estado() == null) {
                if (actual != null) {
                    clase.getAsistencias().remove(actual);
                }
            } else if (actual != null) {
                actual.setEstado(registro.estado());
                actual.setObservacion(texto(registro.observacion()));
            } else {
                clase.getAsistencias().add(Asistencia.builder()
                        .clase(clase)
                        .estudiante(estudiante)
                        .estado(registro.estado())
                        .observacion(texto(registro.observacion()))
                        .build());
            }
        }
    }

    private ClaseDetalleResponse detalle(Clase clase) {
        Map<Long, Asistencia> porEstudiante = clase.getAsistencias().stream()
                .collect(Collectors.toMap(asistencia -> asistencia.getEstudiante().getId(), Function.identity()));
        List<ClaseDetalleResponse.Registro> registros = inscriptos(clase.getMateria().getId()).stream()
                .map(estudiante -> {
                    Asistencia asistencia = porEstudiante.get(estudiante.getId());
                    return new ClaseDetalleResponse.Registro(estudiante.getId(), estudiante.getMatricula(),
                            estudiante.getNombreCompleto(),
                            asistencia == null ? null : asistencia.getEstado(),
                            asistencia == null ? null : asistencia.getObservacion());
                })
                .toList();
        return new ClaseDetalleResponse(ClaseResponse.from(clase), registros);
    }

    // ======================= Resumen por materia =======================

    @Transactional(readOnly = true)
    public ResumenAsistenciaResponse resumen(Long materiaId, String username) {
        Materia materia = materiaConPermiso(materiaId, username);
        Map<Long, List<EstadoAsistencia>> estados = asistenciaRepository.findByClaseMateriaId(materiaId).stream()
                .collect(Collectors.groupingBy(asistencia -> asistencia.getEstudiante().getId(),
                        Collectors.mapping(Asistencia::getEstado, Collectors.toList())));
        Map<Long, Exoneracion> exoneraciones = exoneracionRepository.findByMateriaIdIn(List.of(materiaId)).stream()
                .collect(Collectors.toMap(exoneracion -> exoneracion.getEstudiante().getId(), Function.identity()));

        List<FilaAsistenciaResponse> filas = inscriptos(materiaId).stream()
                .map(estudiante -> {
                    Exoneracion exoneracion = exoneraciones.get(estudiante.getId());
                    CalculoAsistencia.Resultado resultado = CalculoAsistencia.calcular(
                            estados.getOrDefault(estudiante.getId(), List.of()), materia.asistenciaMinimaEfectiva(),
                            materia.getClasesPrevistas(), exoneracion != null);
                    return FilaAsistenciaResponse.from(estudiante, resultado, exoneracion);
                })
                .toList();
        return new ResumenAsistenciaResponse(materia.getId(), materia.getCodigo(), materia.getNombre(),
                materia.asistenciaMinimaEfectiva(), claseRepository.countByMateriaId(materiaId),
                materia.getClasesPrevistas(), filas);
    }

    /**
     * Planilla de asistencia: fechas de las clases (de la más antigua a la más reciente) y, por estudiante,
     * su estado en cada una ({@code null} = sin registrar), en el mismo orden.
     */
    public record Matriz(List<LocalDate> fechas, Map<Long, List<EstadoAsistencia>> estados) {
    }

    @Transactional(readOnly = true)
    public Matriz matriz(Long materiaId, String username) {
        materiaConPermiso(materiaId, username);
        List<Clase> clases = new ArrayList<>(claseRepository.findByMateriaIdOrderByFechaDesc(materiaId));
        clases.sort(Comparator.comparing(Clase::getFecha));
        Map<Long, Integer> columna = new HashMap<>();
        for (int i = 0; i < clases.size(); i++) {
            columna.put(clases.get(i).getId(), i);
        }

        Map<Long, List<EstadoAsistencia>> estados = new HashMap<>();
        for (Estudiante estudiante : inscriptos(materiaId)) {
            estados.put(estudiante.getId(), new ArrayList<>(java.util.Collections.nCopies(clases.size(), null)));
        }
        for (Asistencia asistencia : asistenciaRepository.findByClaseMateriaId(materiaId)) {
            List<EstadoAsistencia> fila = estados.get(asistencia.getEstudiante().getId());
            if (fila != null) {
                fila.set(columna.get(asistencia.getClase().getId()), asistencia.getEstado());
            }
        }
        return new Matriz(clases.stream().map(Clase::getFecha).toList(), estados);
    }

    /** Asistencia de un estudiante en una materia (para decidir si puede rendir el examen final). */
    @Transactional(readOnly = true)
    public CalculoAsistencia.Resultado resultado(Long estudianteId, Materia materia) {
        List<EstadoAsistencia> estados = asistenciaRepository
                .findByEstudianteIdAndClaseMateriaId(estudianteId, materia.getId()).stream()
                .map(Asistencia::getEstado)
                .toList();
        return CalculoAsistencia.calcular(estados, materia.asistenciaMinimaEfectiva(), materia.getClasesPrevistas(),
                exoneracionRepository.existsByEstudianteIdAndMateriaId(estudianteId, materia.getId()));
    }

    // ======================= Estudiante =======================

    /** Asistencia del estudiante autenticado en cada materia en la que está inscripto. */
    @Transactional(readOnly = true)
    public List<MiAsistenciaResponse> miAsistencia(String username) {
        Estudiante estudiante = estudianteRepository.findByUsuarioUsername(username)
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante para el usuario", username));
        List<Inscripcion> inscripciones = inscripcionRepository
                .findByEstudianteIdOrderByMateriaSemestreAscMateriaNombreAsc(estudiante.getId());
        if (inscripciones.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Asistencia>> porMateria = asistenciaRepository.findByEstudianteId(estudiante.getId()).stream()
                .collect(Collectors.groupingBy(asistencia -> asistencia.getClase().getMateria().getId()));
        Map<Long, Exoneracion> exoneraciones = exoneracionRepository.findByEstudianteId(estudiante.getId()).stream()
                .collect(Collectors.toMap(exoneracion -> exoneracion.getMateria().getId(), Function.identity()));
        Map<Long, Long> clasesDictadas = contar(claseRepository.contarPorMateria(
                inscripciones.stream().map(inscripcion -> inscripcion.getMateria().getId()).toList()));

        return inscripciones.stream()
                .map(inscripcion -> {
                    Materia materia = inscripcion.getMateria();
                    List<Asistencia> asistencias = porMateria.getOrDefault(materia.getId(), List.of());
                    Exoneracion exoneracion = exoneraciones.get(materia.getId());
                    CalculoAsistencia.Resultado resultado = CalculoAsistencia.calcular(
                            asistencias.stream().map(Asistencia::getEstado).toList(),
                            materia.asistenciaMinimaEfectiva(), materia.getClasesPrevistas(), exoneracion != null);
                    List<MiAsistenciaResponse.ClaseAsistida> clases = asistencias.stream()
                            .sorted(Comparator.comparing((Asistencia a) -> a.getClase().getFecha()).reversed())
                            .map(a -> new MiAsistenciaResponse.ClaseAsistida(a.getClase().getFecha(),
                                    a.getClase().getTema(), a.getEstado(), a.getObservacion()))
                            .toList();
                    return new MiAsistenciaResponse(materia.getId(), materia.getCodigo(), materia.getNombre(),
                            materia.getSemestre(),
                            materia.getDocente() == null ? null : materia.getDocente().getNombreCompleto(),
                            materia.asistenciaMinimaEfectiva(), clasesDictadas.getOrDefault(materia.getId(), 0L),
                            materia.getClasesPrevistas(), FilaAsistenciaResponse.from(estudiante, resultado, exoneracion),
                            clases);
                })
                .toList();
    }

    // ======================= Reporte =======================

    /** Indicadores de las materias visibles: todas para un ADMIN, las que tiene a cargo para un docente. */
    @Transactional(readOnly = true)
    public ReporteAsistenciaResponse reporte(String username) {
        List<Materia> materias = permisosService.esAdmin(username)
                ? materiaRepository.findAllByOrderBySemestreAscNombreAsc()
                : materiaRepository.findByDocenteUsuarioUsernameOrderBySemestreAscNombreAsc(username);
        if (materias.isEmpty()) {
            return new ReporteAsistenciaResponse(0, 0, null, 0, 0, 0, List.of(), List.of(), List.of());
        }
        List<Long> ids = materias.stream().map(Materia::getId).toList();

        Map<Long, List<Estudiante>> inscriptosPorMateria = inscripcionRepository.findByMateriaIdIn(ids).stream()
                .collect(Collectors.groupingBy(inscripcion -> inscripcion.getMateria().getId(),
                        Collectors.mapping(Inscripcion::getEstudiante, Collectors.toList())));
        // materia -> estudiante -> estados
        Map<Long, Map<Long, List<EstadoAsistencia>>> estados = new HashMap<>();
        for (Asistencia asistencia : asistenciaRepository.findByClaseMateriaIdIn(ids)) {
            estados.computeIfAbsent(asistencia.getClase().getMateria().getId(), id -> new HashMap<>())
                    .computeIfAbsent(asistencia.getEstudiante().getId(), id -> new ArrayList<>())
                    .add(asistencia.getEstado());
        }
        Set<String> exonerados = exoneracionRepository.findByMateriaIdIn(ids).stream()
                .map(exoneracion -> clave(exoneracion.getMateria().getId(), exoneracion.getEstudiante().getId()))
                .collect(Collectors.toSet());
        Map<Long, Long> clasesDictadas = contar(claseRepository.contarPorMateria(ids));

        List<ReporteAsistenciaResponse.PorMateria> porMateria = new ArrayList<>();
        List<ReporteAsistenciaResponse.Alerta> alertas = new ArrayList<>();
        Map<Long, List<CalculoAsistencia.Resultado>> resultadosPorCarrera = new LinkedHashMap<>();
        Map<Long, Long> materiasPorCarrera = new LinkedHashMap<>();
        Map<Long, String> nombresDeCarrera = new HashMap<>();
        List<CalculoAsistencia.Resultado> todos = new ArrayList<>();

        for (Materia materia : materias) {
            int minimo = materia.asistenciaMinimaEfectiva();
            List<CalculoAsistencia.Resultado> resultados = new ArrayList<>();
            for (Estudiante estudiante : inscriptosPorMateria.getOrDefault(materia.getId(), List.of())) {
                CalculoAsistencia.Resultado resultado = CalculoAsistencia.calcular(
                        estados.getOrDefault(materia.getId(), Map.of()).getOrDefault(estudiante.getId(), List.of()),
                        minimo, materia.getClasesPrevistas(),
                        exonerados.contains(clave(materia.getId(), estudiante.getId())));
                resultados.add(resultado);
                if (resultado.condicion() == CalculoAsistencia.Condicion.EN_RIESGO
                        || resultado.condicion() == CalculoAsistencia.Condicion.NO_HABILITADO) {
                    alertas.add(new ReporteAsistenciaResponse.Alerta(estudiante.getId(), estudiante.getMatricula(),
                            estudiante.getNombreCompleto(), materia.getId(), materia.getCodigo(), materia.getNombre(),
                            resultado.porcentaje(), resultado.faltasRestantes(), minimo, resultado.condicion()));
                }
            }
            porMateria.add(new ReporteAsistenciaResponse.PorMateria(materia.getId(), materia.getCodigo(),
                    materia.getNombre(), materia.getCarrera().getNombre(),
                    materia.getDocente() == null ? null : materia.getDocente().getNombreCompleto(),
                    resultados.size(), clasesDictadas.getOrDefault(materia.getId(), 0L), materia.getClasesPrevistas(),
                    minimo, promedio(resultados),
                    contar(resultados, CalculoAsistencia.Condicion.HABILITADO),
                    contar(resultados, CalculoAsistencia.Condicion.EN_RIESGO),
                    contar(resultados, CalculoAsistencia.Condicion.NO_HABILITADO),
                    contar(resultados, CalculoAsistencia.Condicion.EXONERADO)));
            Carrera carrera = materia.getCarrera();
            nombresDeCarrera.put(carrera.getId(), carrera.getNombre());
            resultadosPorCarrera.computeIfAbsent(carrera.getId(), id -> new ArrayList<>()).addAll(resultados);
            materiasPorCarrera.merge(carrera.getId(), 1L, Long::sum);
            todos.addAll(resultados);
        }

        List<ReporteAsistenciaResponse.PorCarrera> porCarrera = resultadosPorCarrera.entrySet().stream()
                .map(entrada -> new ReporteAsistenciaResponse.PorCarrera(entrada.getKey(),
                        nombresDeCarrera.get(entrada.getKey()), materiasPorCarrera.get(entrada.getKey()),
                        entrada.getValue().size(),
                        promedio(entrada.getValue()),
                        contar(entrada.getValue(), CalculoAsistencia.Condicion.EN_RIESGO),
                        contar(entrada.getValue(), CalculoAsistencia.Condicion.NO_HABILITADO)))
                .sorted(Comparator.comparing(ReporteAsistenciaResponse.PorCarrera::nombre))
                .toList();
        alertas.sort(Comparator.comparing(ReporteAsistenciaResponse.Alerta::porcentaje,
                Comparator.nullsLast(Comparator.naturalOrder())));

        return new ReporteAsistenciaResponse(
                clasesDictadas.values().stream().mapToLong(Long::longValue).sum(), todos.size(), promedio(todos),
                contar(todos, CalculoAsistencia.Condicion.EN_RIESGO),
                contar(todos, CalculoAsistencia.Condicion.NO_HABILITADO),
                contar(todos, CalculoAsistencia.Condicion.EXONERADO),
                porMateria, porCarrera, alertas.stream().limit(MAXIMO_ALERTAS).toList());
    }

    // ======================= Exoneraciones =======================

    @Transactional(readOnly = true)
    public List<ExoneracionResponse> listarExoneraciones(String username) {
        List<Exoneracion> exoneraciones = permisosService.esAdmin(username)
                ? exoneracionRepository.findAllByOrderByFechaRegistroDesc()
                : exoneracionRepository.findByMateriaDocenteUsuarioUsernameOrderByFechaRegistroDesc(username);
        return exoneraciones.stream().map(ExoneracionResponse::from).toList();
    }

    /** Solo un ADMIN; hasta {@link Exoneracion#MAXIMO_POR_SEMESTRE} materias del mismo semestre por estudiante. */
    @Transactional
    public ExoneracionResponse exonerar(ExoneracionRequest request, String username) {
        verificarAdmin(username);
        Materia materia = buscarMateria(request.materiaId());
        Estudiante estudiante = estudianteRepository.findById(request.estudianteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Estudiante", request.estudianteId()));
        if (!inscripcionRepository.existsByEstudianteIdAndMateriaId(estudiante.getId(), materia.getId())) {
            throw new ConflictoException("%s no está inscripto en %s"
                    .formatted(estudiante.getNombreCompleto(), materia.getCodigo()));
        }
        if (exoneracionRepository.existsByEstudianteIdAndMateriaId(estudiante.getId(), materia.getId())) {
            throw new ConflictoException("%s ya está exonerado de asistencia en %s"
                    .formatted(estudiante.getNombreCompleto(), materia.getCodigo()));
        }
        long delSemestre = exoneracionRepository.countByEstudianteIdAndMateriaSemestre(estudiante.getId(),
                materia.getSemestre());
        if (delSemestre >= Exoneracion.MAXIMO_POR_SEMESTRE) {
            throw new ConflictoException("%s ya tiene %d materias del %d° semestre exoneradas (máximo del reglamento)"
                    .formatted(estudiante.getNombreCompleto(), delSemestre, materia.getSemestre()));
        }

        Exoneracion exoneracion = exoneracionRepository.saveAndFlush(Exoneracion.builder()
                .estudiante(estudiante)
                .materia(materia)
                .motivo(request.motivo().trim())
                .otorgadaPor(username)
                .build());
        return ExoneracionResponse.from(exoneracion);
    }

    @Transactional
    public void quitarExoneracion(Long id, String username) {
        verificarAdmin(username);
        Exoneracion exoneracion = exoneracionRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Exoneración", id));
        exoneracionRepository.delete(exoneracion);
    }

    // ======================= Utilidades =======================

    private List<Estudiante> inscriptos(Long materiaId) {
        return inscripcionRepository.findByMateriaIdOrderByEstudianteNombreCompletoAsc(materiaId).stream()
                .map(Inscripcion::getEstudiante)
                .toList();
    }

    private Materia materiaConPermiso(Long materiaId, String username) {
        Materia materia = buscarMateria(materiaId);
        permisosService.verificarMateria(materia, username);
        return materia;
    }

    private Clase claseConPermiso(Long claseId, String username) {
        Clase clase = claseRepository.findWithMateriaById(claseId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Clase", claseId));
        permisosService.verificarMateria(clase.getMateria(), username);
        return clase;
    }

    private Materia buscarMateria(Long id) {
        return materiaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Materia", id));
    }

    private void verificarAdmin(String username) {
        if (!permisosService.esAdmin(username)) {
            throw new OperacionNoPermitidaException("Solo un administrador puede gestionar exoneraciones de asistencia");
        }
    }

    private static void validarFecha(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now())) {
            throw new DatoInvalidoException("No se puede registrar la asistencia de una fecha futura");
        }
    }

    private static String texto(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : null;
    }

    private static String clave(Long materiaId, Long estudianteId) {
        return materiaId + ":" + estudianteId;
    }

    private static Map<Long, Long> contar(List<Object[]> filas) {
        return filas.stream().collect(Collectors.toMap(fila -> (Long) fila[0], fila -> (Long) fila[1]));
    }

    private static long contar(Collection<CalculoAsistencia.Resultado> resultados, CalculoAsistencia.Condicion condicion) {
        return resultados.stream().filter(resultado -> resultado.condicion() == condicion).count();
    }

    /** Promedio de los porcentajes de quienes tienen clases registradas, con un decimal. */
    private static Double promedio(Collection<CalculoAsistencia.Resultado> resultados) {
        return resultados.stream()
                .map(CalculoAsistencia.Resultado::porcentaje)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .map(valor -> Math.round(valor * 10) / 10.0)
                .boxed()
                .findFirst()
                .orElse(null);
    }
}
