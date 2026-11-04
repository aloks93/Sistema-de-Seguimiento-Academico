package py.edu.upe.hernandarias.excel;

import static py.edu.upe.hernandarias.excel.ExcelUtil.PRIMERA_FILA_DATOS;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import py.edu.upe.hernandarias.dto.DocenteRequest;
import py.edu.upe.hernandarias.dto.DocenteResponse;
import py.edu.upe.hernandarias.dto.EstudianteRequest;
import py.edu.upe.hernandarias.dto.MateriaRequest;
import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.excel.ExcelUtil.Estilos;
import py.edu.upe.hernandarias.excel.ResultadoImportacion.Observacion;
import py.edu.upe.hernandarias.exception.DatoInvalidoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;
import py.edu.upe.hernandarias.service.DocenteService;
import py.edu.upe.hernandarias.service.EstudianteService;
import py.edu.upe.hernandarias.service.MateriaService;

/**
 * Carga masiva por Excel. El sistema genera una plantilla por carrera (docentes, materias con
 * filas preparadas para cada semestre y estudiantes); el ADMIN la completa y la sube.
 * <p>
 * La importación es "todo o nada": primero se valida cada fila; si hay un solo error no se guarda
 * nada. Lo que ya existe (mismo usuario, código o matrícula en esa carrera) se omite sin tocarlo,
 * así la misma plantilla se puede volver a subir después de agregarle filas.
 */
@Service
@RequiredArgsConstructor
public class CargaMasivaService {

    static final String HOJA_INSTRUCCIONES = "Instrucciones";
    static final String HOJA_DOCENTES = "Docentes";
    static final String HOJA_MATERIAS = "Materias";
    static final String HOJA_ESTUDIANTES = "Estudiantes";
    static final String HOJA_DATOS = "datos";

    static final List<String> COLUMNAS_DOCENTES = List.of("Nombre completo", "Usuario", "Contraseña inicial", "Observación");
    static final List<String> COLUMNAS_MATERIAS =
            List.of("Código", "Nombre", "Semestre", "Créditos", "Usuario del docente", "Observación");
    static final List<String> COLUMNAS_ESTUDIANTES =
            List.of("Nombre completo", "Matrícula", "Usuario", "Contraseña inicial", "Observación");

    private static final int FILAS_VACIAS_DOCENTES = 15;
    private static final int FILAS_VACIAS_POR_SEMESTRE = 6;
    private static final int FILAS_VACIAS_ESTUDIANTES = 60;
    private static final int MAXIMO_FILAS = 3000;

    private final CarreraRepository carreraRepository;
    private final DocenteRepository docenteRepository;
    private final MateriaRepository materiaRepository;
    private final EstudianteRepository estudianteRepository;
    private final UsuarioRepository usuarioRepository;
    private final DocenteService docenteService;
    private final MateriaService materiaService;
    private final EstudianteService estudianteService;

    // ======================= Plantilla =======================

    @Transactional(readOnly = true)
    public ArchivoExcel generarPlantilla(Long carreraId) {
        Carrera carrera = carreraRepository.findById(carreraId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Carrera", carreraId));

        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Estilos estilos = ExcelUtil.estilos(libro);
            hojaInstrucciones(libro, estilos, carrera);
            int ultimaFilaDocentes = hojaDocentes(libro, estilos);
            hojaMaterias(libro, estilos, carrera, ultimaFilaDocentes);
            hojaEstudiantes(libro, estilos, carrera);

            // Hoja oculta que identifica la carrera al subir el archivo
            Sheet datos = libro.createSheet(HOJA_DATOS);
            ExcelUtil.celda(datos, 0, 0, "carrera", null);
            ExcelUtil.celda(datos, 0, 1, carrera.getCodigo(), null);
            libro.setSheetVisibility(libro.getSheetIndex(datos), SheetVisibility.VERY_HIDDEN);
            libro.setActiveSheet(0);

            return new ArchivoExcel(ExcelUtil.nombreArchivo("plantilla-" + carrera.getCodigo()), ExcelUtil.aBytes(libro));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la plantilla", e);
        }
    }

    private void hojaInstrucciones(Workbook libro, Estilos estilos, Carrera carrera) {
        Sheet hoja = libro.createSheet(HOJA_INSTRUCCIONES);
        ExcelUtil.celda(hoja, 0, 0, "Plantilla de carga masiva · " + carrera.getNombre() + " (" + carrera.getCodigo() + ")", estilos.titulo());
        String[] lineas = {
            "Duración de la carrera: " + carrera.getSemestres() + " semestres.",
            "",
            "CÓMO USARLA",
            "1. Complete las hojas Docentes, Materias y Estudiantes. Las filas en gris ya están registradas: no hace falta tocarlas.",
            "2. Docentes: nombre completo, usuario y contraseña inicial (mínimo 6 caracteres).",
            "3. Materias: ya hay filas preparadas para cada semestre; complete código, nombre y créditos.",
            "   En 'Usuario del docente' elija de la lista un docente de la hoja Docentes (o déjelo vacío).",
            "4. Estudiantes: nombre completo, matrícula, usuario y contraseña inicial (mínimo 6 caracteres).",
            "5. Guarde el archivo y súbalo en el sistema: Carga masiva → Subir plantilla.",
            "",
            "IMPORTANTE",
            "• Las filas vacías se ignoran. No cambie los nombres de las hojas ni los encabezados.",
            "• El sistema primero revisa todo y muestra los errores con su hoja y fila. Si hay un error, no se guarda nada.",
            "• Lo que ya existe (mismo usuario, código o matrícula) se omite: puede volver a subir esta misma plantilla con filas nuevas.",
            "• Los usuarios deben cambiar su contraseña inicial desde Ajustes al ingresar por primera vez.",
        };
        for (int i = 0; i < lineas.length; i++) {
            ExcelUtil.celda(hoja, i + 2, 0, lineas[i], lineas[i].equals(lineas[i].toUpperCase()) && !lineas[i].isBlank()
                    ? estilos.destacado() : null);
        }
        hoja.setColumnWidth(0, 120 * 256);
    }

    /** Devuelve el índice (0) de la última fila con datos posibles, para la lista desplegable de docentes. */
    private int hojaDocentes(Workbook libro, Estilos estilos) {
        Sheet hoja = libro.createSheet(HOJA_DOCENTES);
        ExcelUtil.encabezado(hoja, estilos, "Docentes", "Gris = ya registrados. Complete las filas vacías con docentes nuevos.", COLUMNAS_DOCENTES);
        int fila = PRIMERA_FILA_DATOS;
        for (Docente docente : docenteRepository.findAllByOrderByNombreCompletoAsc()) {
            ExcelUtil.fila(hoja, fila++, List.of(docente.getNombreCompleto(), docente.getUsuario().getUsername(), "", "Ya registrado"),
                    estilos.existente());
        }
        for (int i = 0; i < FILAS_VACIAS_DOCENTES; i++) {
            ExcelUtil.fila(hoja, fila++, List.of("", "", "", ""), estilos.texto());
        }
        anchos(hoja, 34, 20, 20, 18);
        return fila - 1;
    }

    private void hojaMaterias(Workbook libro, Estilos estilos, Carrera carrera, int ultimaFilaDocentes) {
        Sheet hoja = libro.createSheet(HOJA_MATERIAS);
        ExcelUtil.encabezado(hoja, estilos, "Materias de " + carrera.getNombre(),
                "Hay filas preparadas para cada uno de los " + carrera.getSemestres() + " semestres. Gris = ya registradas.",
                COLUMNAS_MATERIAS);
        int fila = PRIMERA_FILA_DATOS;
        for (Materia materia : materiaRepository.findByCarreraIdOrderBySemestreAscNombreAsc(carrera.getId())) {
            ExcelUtil.fila(hoja, fila++, List.of(materia.getCodigo(), materia.getNombre(), materia.getSemestre(), materia.getCreditos(),
                    materia.getDocente() == null ? "" : materia.getDocente().getUsuario().getUsername(), "Ya registrada"),
                    estilos.existente());
        }
        for (int semestre = 1; semestre <= carrera.getSemestres(); semestre++) {
            for (int i = 0; i < FILAS_VACIAS_POR_SEMESTRE; i++) {
                ExcelUtil.fila(hoja, fila++, List.of("", "", semestre, "", "", ""), estilos.texto());
            }
        }
        int ultima = fila - 1;

        DataValidationHelper ayuda = hoja.getDataValidationHelper();
        String[] semestres = IntStream.rangeClosed(1, carrera.getSemestres()).mapToObj(String::valueOf).toArray(String[]::new);
        validacion(hoja, ayuda.createExplicitListConstraint(semestres), 2, ultima,
                "Semestre", "Elija un semestre entre 1 y " + carrera.getSemestres());
        validacion(hoja, ayuda.createIntegerConstraint(DataValidationConstraint.OperatorType.BETWEEN, "1", "30"), 3, ultima,
                "Créditos", "Número entero de 1 a 30");
        validacion(hoja, ayuda.createFormulaListConstraint(
                        "%s!$B$%d:$B$%d".formatted(HOJA_DOCENTES, PRIMERA_FILA_DATOS + 1, ultimaFilaDocentes + 1)), 4, ultima,
                "Docente", "Elija el usuario de un docente de la hoja Docentes, o déjelo vacío");
        anchos(hoja, 14, 34, 11, 11, 22, 18);
    }

    private void hojaEstudiantes(Workbook libro, Estilos estilos, Carrera carrera) {
        Sheet hoja = libro.createSheet(HOJA_ESTUDIANTES);
        ExcelUtil.encabezado(hoja, estilos, "Estudiantes de " + carrera.getNombre(),
                "Gris = ya registrados. Complete las filas vacías con estudiantes nuevos.", COLUMNAS_ESTUDIANTES);
        int fila = PRIMERA_FILA_DATOS;
        for (Estudiante estudiante : estudianteRepository.findByCarreraIdOrderByNombreCompletoAsc(carrera.getId())) {
            ExcelUtil.fila(hoja, fila++, List.of(estudiante.getNombreCompleto(), estudiante.getMatricula(),
                    estudiante.getUsuario().getUsername(), "", "Ya registrado"), estilos.existente());
        }
        for (int i = 0; i < FILAS_VACIAS_ESTUDIANTES; i++) {
            ExcelUtil.fila(hoja, fila++, List.of("", "", "", "", ""), estilos.texto());
        }
        anchos(hoja, 34, 16, 20, 20, 18);
    }

    private static void validacion(Sheet hoja, DataValidationConstraint regla, int columna, int ultimaFila,
                                   String titulo, String mensaje) {
        var validacion = hoja.getDataValidationHelper().createValidation(regla,
                new CellRangeAddressList(PRIMERA_FILA_DATOS, ultimaFila, columna, columna));
        validacion.setShowErrorBox(true);
        validacion.createErrorBox(titulo, mensaje);
        validacion.setShowPromptBox(true);
        validacion.createPromptBox(titulo, mensaje);
        hoja.addValidationData(validacion);
    }

    private static void anchos(Sheet hoja, int... caracteres) {
        for (int i = 0; i < caracteres.length; i++) {
            hoja.setColumnWidth(i, caracteres[i] * 256);
        }
    }

    // ======================= Importación =======================

    private record NuevoDocente(String nombre, String usuario, String password) {
    }

    private record NuevaMateria(String codigo, String nombre, int semestre, int creditos, String docente) {
    }

    private record NuevoEstudiante(String nombre, String matricula, String usuario, String password) {
    }

    /**
     * Valida la plantilla y, si no hay errores y {@code soloValidar} es false, crea todo en una
     * sola transacción.
     */
    @Transactional
    public ResultadoImportacion importar(MultipartFile archivo, boolean soloValidar, String usernameActual) {
        try (Workbook libro = abrir(archivo)) {
            Carrera carrera = carreraDelLibro(libro);
            List<Observacion> observaciones = new ArrayList<>();
            Set<String> usuariosNuevos = new HashSet<>();

            List<NuevoDocente> docentes = leerDocentes(hoja(libro, HOJA_DOCENTES, COLUMNAS_DOCENTES), observaciones, usuariosNuevos);
            Set<String> docentesDisponibles = new HashSet<>();
            docenteRepository.findAll().forEach(d -> docentesDisponibles.add(d.getUsuario().getUsername()));
            docentes.forEach(d -> docentesDisponibles.add(d.usuario()));

            List<NuevaMateria> materias = leerMaterias(hoja(libro, HOJA_MATERIAS, COLUMNAS_MATERIAS), carrera, docentesDisponibles, observaciones);
            List<NuevoEstudiante> estudiantes = leerEstudiantes(hoja(libro, HOJA_ESTUDIANTES, COLUMNAS_ESTUDIANTES), carrera,
                    observaciones, usuariosNuevos);

            int errores = (int) observaciones.stream().filter(o -> o.tipo().equals("ERROR")).count();
            int omitidos = observaciones.size() - errores;
            boolean aplicar = errores == 0 && !soloValidar;
            if (aplicar) {
                guardar(carrera, docentes, materias, estudiantes, usernameActual);
            }
            return new ResultadoImportacion(carrera.getNombre(), aplicar, docentes.size(), materias.size(), estudiantes.size(),
                    omitidos, errores, observaciones);
        } catch (IOException e) {
            throw new DatoInvalidoException("No se pudo leer el archivo");
        }
    }

    private void guardar(Carrera carrera, List<NuevoDocente> docentes, List<NuevaMateria> materias,
                         List<NuevoEstudiante> estudiantes, String usernameActual) {
        // Se reutilizan los servicios para aplicar exactamente las mismas reglas que la API
        Map<String, Long> idDocentePorUsuario = new HashMap<>();
        docenteRepository.findAll().forEach(d -> idDocentePorUsuario.put(d.getUsuario().getUsername(), d.getId()));
        for (NuevoDocente docente : docentes) {
            DocenteResponse creado = docenteService.crear(new DocenteRequest(docente.usuario(), docente.password(), docente.nombre()));
            idDocentePorUsuario.put(creado.username(), creado.id());
        }
        for (NuevaMateria materia : materias) {
            materiaService.crear(new MateriaRequest(materia.codigo(), materia.nombre(), materia.creditos(), materia.semestre(),
                    carrera.getId(), materia.docente() == null ? null : idDocentePorUsuario.get(materia.docente()),
                    null, null));
        }
        for (NuevoEstudiante estudiante : estudiantes) {
            estudianteService.crear(new EstudianteRequest(estudiante.usuario(), estudiante.password(), estudiante.matricula(),
                    estudiante.nombre(), carrera.getId()), usernameActual);
        }
    }

    private List<NuevoDocente> leerDocentes(Sheet hoja, List<Observacion> observaciones, Set<String> usuariosNuevos) {
        Map<String, Docente> existentes = docenteRepository.findAll().stream()
                .collect(Collectors.toMap(d -> d.getUsuario().getUsername(), Function.identity()));
        List<NuevoDocente> nuevos = new ArrayList<>();
        recorrer(hoja, 3, (fila, celdas) -> {
            String nombre = celdas.get(0);
            String usuario = celdas.get(1);
            String password = celdas.get(2);
            List<String> errores = new ArrayList<>();
            if (usuario.isEmpty()) {
                errores.add("falta el usuario");
            } else if (existentes.containsKey(usuario)) {
                observaciones.add(omitido(HOJA_DOCENTES, fila, "El docente " + usuario + " ya existe"));
                return;
            } else if (!usuariosNuevos.add(usuario)) {
                errores.add("el usuario " + usuario + " está repetido en el archivo");
            } else if (usuarioRepository.existsByUsername(usuario)) {
                errores.add("el usuario " + usuario + " ya lo usa otra cuenta");
            }
            validarTexto(errores, "nombre completo", nombre, 150);
            validarTexto(errores, "usuario", usuario, 50);
            validarPassword(errores, password);
            if (errores.isEmpty()) {
                nuevos.add(new NuevoDocente(nombre, usuario, password));
            } else {
                observaciones.add(error(HOJA_DOCENTES, fila, errores));
            }
        });
        return nuevos;
    }

    private List<NuevaMateria> leerMaterias(Sheet hoja, Carrera carrera, Set<String> docentesDisponibles,
                                            List<Observacion> observaciones) {
        Map<String, Materia> existentes = materiaRepository.findAll().stream()
                .collect(Collectors.toMap(Materia::getCodigo, Function.identity()));
        Set<String> codigos = new HashSet<>();
        List<NuevaMateria> nuevas = new ArrayList<>();
        // El semestre viene precargado en las filas vacías: la fila cuenta solo si tiene código o nombre
        recorrer(hoja, 5, (fila, celdas) -> {
            String codigo = celdas.get(0);
            String nombre = celdas.get(1);
            if (codigo.isEmpty() && nombre.isEmpty()) {
                return;
            }
            List<String> errores = new ArrayList<>();
            if (codigo.isEmpty()) {
                errores.add("falta el código");
            } else if (existentes.containsKey(codigo)) {
                Materia existente = existentes.get(codigo);
                if (existente.getCarrera().getId().equals(carrera.getId())) {
                    observaciones.add(omitido(HOJA_MATERIAS, fila, "La materia " + codigo + " ya existe"));
                } else {
                    observaciones.add(error(HOJA_MATERIAS, fila,
                            List.of("el código " + codigo + " ya pertenece a " + existente.getCarrera().getNombre())));
                }
                return;
            } else if (!codigos.add(codigo)) {
                errores.add("el código " + codigo + " está repetido en el archivo");
            }
            validarTexto(errores, "código", codigo, 20);
            validarTexto(errores, "nombre", nombre, 120);
            Integer semestre = entero(errores, "semestre", celdas.get(2), 1, carrera.getSemestres());
            Integer creditos = entero(errores, "créditos", celdas.get(3), 1, 30);
            String docente = celdas.get(4).isEmpty() ? null : celdas.get(4);
            if (docente != null && !docentesDisponibles.contains(docente)) {
                errores.add("el docente " + docente + " no existe (agréguelo en la hoja Docentes)");
            }
            if (errores.isEmpty()) {
                nuevas.add(new NuevaMateria(codigo, nombre, semestre, creditos, docente));
            } else {
                observaciones.add(error(HOJA_MATERIAS, fila, errores));
            }
        });
        return nuevas;
    }

    private List<NuevoEstudiante> leerEstudiantes(Sheet hoja, Carrera carrera, List<Observacion> observaciones,
                                                  Set<String> usuariosNuevos) {
        Map<String, Estudiante> existentes = estudianteRepository.findAll().stream()
                .collect(Collectors.toMap(Estudiante::getMatricula, Function.identity()));
        Set<String> matriculas = new HashSet<>();
        List<NuevoEstudiante> nuevos = new ArrayList<>();
        recorrer(hoja, 4, (fila, celdas) -> {
            String nombre = celdas.get(0);
            String matricula = celdas.get(1);
            String usuario = celdas.get(2);
            String password = celdas.get(3);
            List<String> errores = new ArrayList<>();
            if (matricula.isEmpty()) {
                errores.add("falta la matrícula");
            } else if (existentes.containsKey(matricula)) {
                Estudiante existente = existentes.get(matricula);
                if (existente.getCarrera().getId().equals(carrera.getId())) {
                    observaciones.add(omitido(HOJA_ESTUDIANTES, fila, "El estudiante con matrícula " + matricula + " ya existe"));
                } else {
                    observaciones.add(error(HOJA_ESTUDIANTES, fila,
                            List.of("la matrícula " + matricula + " ya pertenece a un estudiante de " + existente.getCarrera().getNombre())));
                }
                return;
            } else if (!matriculas.add(matricula)) {
                errores.add("la matrícula " + matricula + " está repetida en el archivo");
            }
            if (usuario.isEmpty()) {
                errores.add("falta el usuario");
            } else if (!usuariosNuevos.add(usuario)) {
                errores.add("el usuario " + usuario + " está repetido en el archivo");
            } else if (usuarioRepository.existsByUsername(usuario)) {
                errores.add("el usuario " + usuario + " ya existe");
            }
            validarTexto(errores, "nombre completo", nombre, 150);
            validarTexto(errores, "matrícula", matricula, 20);
            validarTexto(errores, "usuario", usuario, 50);
            validarPassword(errores, password);
            if (errores.isEmpty()) {
                nuevos.add(new NuevoEstudiante(nombre, matricula, usuario, password));
            } else {
                observaciones.add(error(HOJA_ESTUDIANTES, fila, errores));
            }
        });
        return nuevos;
    }

    // ======================= Lectura =======================

    private interface ProcesadorFila {
        void procesar(int filaExcel, List<String> celdas);
    }

    /** Recorre las filas de datos; ignora las vacías. {@code filaExcel} es el número que ve el usuario. */
    private static void recorrer(Sheet hoja, int columnas, ProcesadorFila procesador) {
        DataFormatter formato = new DataFormatter();
        int ultima = Math.min(hoja.getLastRowNum(), PRIMERA_FILA_DATOS + MAXIMO_FILAS);
        for (int i = PRIMERA_FILA_DATOS; i <= ultima; i++) {
            Row fila = hoja.getRow(i);
            if (fila == null) {
                continue;
            }
            List<String> celdas = new ArrayList<>(columnas);
            for (int c = 0; c < columnas; c++) {
                celdas.add(formato.formatCellValue(fila.getCell(c)).trim());
            }
            if (celdas.stream().allMatch(String::isEmpty)) {
                continue;
            }
            procesador.procesar(i + 1, celdas);
        }
    }

    private static Workbook abrir(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new DatoInvalidoException("Elija un archivo Excel (.xlsx)");
        }
        try (InputStream entrada = archivo.getInputStream()) {
            return WorkbookFactory.create(entrada);
        } catch (Exception e) {
            throw new DatoInvalidoException("El archivo no es un Excel válido (.xlsx)");
        }
    }

    private Carrera carreraDelLibro(Workbook libro) {
        Sheet datos = libro.getSheet(HOJA_DATOS);
        String codigo = datos == null || datos.getRow(0) == null ? null
                : new DataFormatter().formatCellValue(datos.getRow(0).getCell(1)).trim();
        if (codigo == null || codigo.isEmpty()) {
            throw new DatoInvalidoException("El archivo no es una plantilla del sistema. Descargue la plantilla de la carrera y complétela");
        }
        return carreraRepository.findAll().stream()
                .filter(c -> c.getCodigo().equals(codigo))
                .findFirst()
                .orElseThrow(() -> new DatoInvalidoException("La carrera " + codigo + " de la plantilla ya no existe"));
    }

    private static Sheet hoja(Workbook libro, String nombre, List<String> columnas) {
        Sheet hoja = libro.getSheet(nombre);
        if (hoja == null) {
            throw new DatoInvalidoException("Falta la hoja " + nombre + ". No cambie los nombres de las hojas de la plantilla");
        }
        Row encabezado = hoja.getRow(ExcelUtil.FILA_ENCABEZADO);
        DataFormatter formato = new DataFormatter();
        for (int i = 0; i < columnas.size() - 1; i++) { // la columna Observación es informativa
            String valor = encabezado == null ? "" : formato.formatCellValue(encabezado.getCell(i)).trim();
            if (!valor.equalsIgnoreCase(columnas.get(i))) {
                throw new DatoInvalidoException("La hoja " + nombre + " no tiene el formato de la plantilla (columna "
                        + (char) ('A' + i) + " debería ser \"" + columnas.get(i) + "\")");
            }
        }
        return hoja;
    }

    // ======================= Validaciones =======================

    private static void validarTexto(List<String> errores, String campo, String valor, int maximo) {
        if (valor.isEmpty()) {
            if (errores.stream().noneMatch(e -> e.contains("falta el " + campo) || e.contains("falta la " + campo))) {
                errores.add("falta " + (campo.equals("matrícula") ? "la " : "el ") + campo);
            }
        } else if (valor.length() > maximo) {
            errores.add("el " + campo + " supera los " + maximo + " caracteres");
        }
    }

    private static void validarPassword(List<String> errores, String password) {
        if (password.length() < 6 || password.length() > 72) {
            errores.add("la contraseña inicial debe tener entre 6 y 72 caracteres");
        }
    }

    private static Integer entero(List<String> errores, String campo, String valor, int minimo, int maximo) {
        try {
            double numero = Double.parseDouble(valor.replace(',', '.'));
            if (numero != Math.rint(numero) || numero < minimo || numero > maximo) {
                throw new NumberFormatException();
            }
            return (int) numero;
        } catch (NumberFormatException e) {
            errores.add("%s debe ser un número entero de %d a %d".formatted(campo, minimo, maximo));
            return null;
        }
    }

    private static Observacion error(String hoja, int fila, List<String> errores) {
        String texto = String.join("; ", errores);
        return new Observacion("ERROR", hoja, fila, Character.toUpperCase(texto.charAt(0)) + texto.substring(1));
    }

    private static Observacion omitido(String hoja, int fila, String mensaje) {
        return new Observacion("OMITIDO", hoja, fila, mensaje + " (se omite)");
    }
}
