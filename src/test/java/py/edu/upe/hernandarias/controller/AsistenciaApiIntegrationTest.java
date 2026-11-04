package py.edu.upe.hernandarias.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import py.edu.upe.hernandarias.BaseDePrueba;
import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AsistenciaApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired EstudianteRepository estudianteRepository;
    @Autowired MateriaRepository materiaRepository;
    @Autowired CalificacionRepository calificacionRepository;
    @Autowired CarreraRepository carreraRepository;
    @Autowired DocenteRepository docenteRepository;

    Carrera informatica;
    Materia programacion;   // a cargo de "profe"
    Materia algoritmos;     // a cargo de "otro"
    Estudiante ana;
    Estudiante beto;
    Estudiante carla;       // de otra carrera
    String profe;
    String admin;

    @BeforeEach
    void prepararDatos() throws Exception {
        limpiar();
        informatica = carreraRepository.save(Carrera.builder().codigo("INF").nombre("Informática").build());
        Carrera administracion = carreraRepository.save(Carrera.builder().codigo("ADM").nombre("Administración").build());
        crearUsuario("admin", Rol.ADMIN);
        Docente docente = docenteRepository.save(Docente.builder()
                .usuario(crearUsuario("profe", Rol.DOCENTE)).nombreCompleto("Carlos Benítez").build());
        Docente otroDocente = docenteRepository.save(Docente.builder()
                .usuario(crearUsuario("otro", Rol.DOCENTE)).nombreCompleto("Otra Docente").build());
        ana = estudiante("ana", "2026-0001", "Ana Pérez", informatica);
        beto = estudiante("beto", "2026-0002", "Beto Ruiz", informatica);
        carla = estudiante("carla", "2026-0003", "Carla Díaz", administracion);
        programacion = materia("INF-101", informatica, docente);
        algoritmos = materia("INF-102", informatica, otroDocente);
        profe = login("profe");
        admin = login("admin");
    }

    @AfterEach
    void limpiar() {
        BaseDePrueba.vaciarAsistenciaEInscripciones(jdbcTemplate);
        calificacionRepository.deleteAll();
        estudianteRepository.deleteAll();
        materiaRepository.deleteAll();
        docenteRepository.deleteAll();
        carreraRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    // ======================= Inscripción =======================

    @Test
    void elDocenteInscribeEstudiantesDeLaCarreraEnSuMateria() throws Exception {
        inscribir(programacion, ana, beto)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inscriptos").value(2));
        inscribir(programacion, ana)
                .andExpect(jsonPath("$.inscriptos").value(0))
                .andExpect(jsonPath("$.yaInscriptos").value(1));
        inscribir(programacion, carla).andExpect(status().isConflict());

        enviar(post("/api/inscripciones/carrera-completa?materiaId=" + programacion.getId()), profe, null)
                .andExpect(jsonPath("$.inscriptos").value(0));
        enviar(get("/api/inscripciones?materiaId=" + programacion.getId()), profe, null)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombreCompleto").value("Ana Pérez"));

        // Otro docente no gestiona esta materia
        enviar(post("/api/inscripciones"), login("otro"), cuerpoInscripcion(programacion, ana))
                .andExpect(status().isForbidden());
    }

    @Test
    void noSeQuitaUnaInscripcionConAsistenciaRegistrada() throws Exception {
        inscribir(programacion, ana, beto);
        pasarLista(LocalDate.now(), "PRESENTE", null).andExpect(status().isCreated());

        JsonNode inscriptos = json(enviar(get("/api/inscripciones?materiaId=" + programacion.getId()), profe, null));
        long conRegistro = inscriptos.get(0).get("id").asLong();
        enviar(delete("/api/inscripciones/" + conRegistro), profe, null).andExpect(status().isConflict());
        assertThat(inscriptos.get(0).get("tieneRegistros").asBoolean()).isTrue();
        assertThat(inscriptos.get(1).get("tieneRegistros").asBoolean()).isFalse();
        enviar(delete("/api/inscripciones/" + inscriptos.get(1).get("id").asLong()), profe, null)
                .andExpect(status().isNoContent());
    }

    // ======================= Asistencia =======================

    @Test
    void calculaElPorcentajeConTardanzasYJustificadas() throws Exception {
        inscribir(programacion, ana, beto);
        // Ana: P T T T -> 3 tardanzas = 1 ausencia -> 75 %; Beto: A A J P -> 3 ausencias -> 25 %
        String[][] dias = {{"PRESENTE", "AUSENTE"}, {"TARDE", "AUSENTE"}, {"TARDE", "JUSTIFICADO"}, {"TARDE", "PRESENTE"}};
        for (int i = 0; i < dias.length; i++) {
            pasarLista(LocalDate.now().minusDays(dias.length - i), dias[i][0], dias[i][1]).andExpect(status().isCreated());
        }

        enviar(get("/api/asistencia/clases?materiaId=" + programacion.getId()), profe, null)
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].tardes").value(1))
                .andExpect(jsonPath("$[0].presentes").value(1));

        enviar(get("/api/asistencia/resumen?materiaId=" + programacion.getId()), profe, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asistenciaMinima").value(70))
                .andExpect(jsonPath("$.clasesDictadas").value(4))
                .andExpect(jsonPath("$.estudiantes[0].nombreEstudiante").value("Ana Pérez"))
                .andExpect(jsonPath("$.estudiantes[0].tardes").value(3))
                .andExpect(jsonPath("$.estudiantes[0].ausenciasComputadas").value(1))
                .andExpect(jsonPath("$.estudiantes[0].porcentaje").value(75.0))
                .andExpect(jsonPath("$.estudiantes[0].condicion").value("EN_RIESGO"))
                .andExpect(jsonPath("$.estudiantes[1].justificados").value(1))
                .andExpect(jsonPath("$.estudiantes[1].porcentaje").value(25.0))
                .andExpect(jsonPath("$.estudiantes[1].condicion").value("NO_HABILITADO"));

        // Con 10 clases previstas al 70 % se permiten 3 ausencias: a Ana le quedan 2
        enviar(put("/api/materias/" + programacion.getId()), admin, """
                {"codigo":"INF-101","nombre":"Programación I","creditos":4,"semestre":1,"carreraId":%d,
                 "docenteId":%d,"clasesPrevistas":10}""".formatted(informatica.getId(), programacion.getDocente().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clasesPrevistas").value(10))
                .andExpect(jsonPath("$.inscriptos").value(2));
        enviar(get("/api/asistencia/resumen?materiaId=" + programacion.getId()), profe, null)
                .andExpect(jsonPath("$.estudiantes[0].faltasRestantes").value(2))
                .andExpect(jsonPath("$.estudiantes[0].condicion").value("EN_RIESGO"));
    }

    @Test
    void validaLaFechaYQueLosEstudiantesEstenInscriptos() throws Exception {
        inscribir(programacion, ana);
        pasarLista(LocalDate.now().plusDays(1), "PRESENTE", null).andExpect(status().isBadRequest());
        pasarLista(LocalDate.now(), "PRESENTE", null).andExpect(status().isCreated());
        pasarLista(LocalDate.now(), "PRESENTE", null).andExpect(status().isConflict());

        String noInscripto = """
                {"materiaId":%d,"fecha":"%s","registros":[{"estudianteId":%d,"estado":"PRESENTE"}]}"""
                .formatted(programacion.getId(), LocalDate.now().minusDays(1), beto.getId());
        enviar(post("/api/asistencia/clases"), profe, noInscripto).andExpect(status().isBadRequest());

        // El otro docente no pasa lista en esta materia
        enviar(post("/api/asistencia/clases"), login("otro"), cuerpoClase(LocalDate.now().minusDays(2), "PRESENTE", null))
                .andExpect(status().isForbidden());
    }

    @Test
    void seCorrigeUnaClaseYSeQuitaUnRegistro() throws Exception {
        inscribir(programacion, ana, beto);
        long clase = json(pasarLista(LocalDate.now(), "AUSENTE", "PRESENTE")).get("clase").get("id").asLong();

        String correccion = """
                {"fecha":"%s","tema":"Funciones","registros":[
                  {"estudianteId":%d,"estado":"JUSTIFICADO","observacion":"Certificado médico"},
                  {"estudianteId":%d,"estado":null}]}""".formatted(LocalDate.now(), ana.getId(), beto.getId());
        enviar(put("/api/asistencia/clases/" + clase), profe, correccion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clase.tema").value("Funciones"))
                .andExpect(jsonPath("$.clase.justificados").value(1))
                .andExpect(jsonPath("$.clase.presentes").value(0))
                .andExpect(jsonPath("$.registros[0].observacion").value("Certificado médico"))
                .andExpect(jsonPath("$.registros[1].estado").doesNotExist());

        enviar(delete("/api/asistencia/clases/" + clase), profe, null).andExpect(status().isNoContent());
        enviar(get("/api/asistencia/clases?materiaId=" + programacion.getId()), profe, null)
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elEstudianteVeSuAsistencia() throws Exception {
        inscribir(programacion, ana);
        pasarLista(LocalDate.now().minusDays(1), "PRESENTE", null);
        pasarLista(LocalDate.now(), "AUSENTE", null);

        enviar(get("/api/estudiantes/me/asistencia"), login("ana"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigoMateria").value("INF-101"))
                .andExpect(jsonPath("$[0].clasesDictadas").value(2))
                .andExpect(jsonPath("$[0].resumen.porcentaje").value(50.0))
                .andExpect(jsonPath("$[0].resumen.condicion").value("NO_HABILITADO"))
                .andExpect(jsonPath("$[0].clases[0].estado").value("AUSENTE"));

        enviar(get("/api/asistencia/resumen?materiaId=" + programacion.getId()), login("ana"), null)
                .andExpect(status().isForbidden());
    }

    // ======================= Examen final y exoneraciones =======================

    @Test
    void soloLosHabilitadosRindenElExamenFinal() throws Exception {
        inscribir(programacion, ana, beto);
        pasarLista(LocalDate.now().minusDays(1), "PRESENTE", "AUSENTE");
        pasarLista(LocalDate.now(), "PRESENTE", "AUSENTE");

        enviar(post("/api/calificaciones"), profe, nota(beto, 10, 10, 15, 40))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no está habilitado")));
        // Sin el examen final sí se cargan sus parciales
        enviar(post("/api/calificaciones"), profe, nota(beto, 10, 10, 15, null)).andExpect(status().isCreated());
        enviar(post("/api/calificaciones"), profe, nota(ana, 12, 13, 18, 45))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota").value(88))
                .andExpect(jsonPath("$.calificacion").value(4));

        // El docente no exonera; el admin sí, y desde entonces Beto puede rendir
        String exoneracion = """
                {"estudianteId":%d,"materiaId":%d,"motivo":"Reposo médico"}""".formatted(beto.getId(), programacion.getId());
        enviar(post("/api/asistencia/exoneraciones"), profe, exoneracion).andExpect(status().isForbidden());
        enviar(post("/api/asistencia/exoneraciones"), admin, exoneracion)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.otorgadaPor").value("admin"));
        enviar(post("/api/asistencia/exoneraciones"), admin, exoneracion).andExpect(status().isConflict());

        long notaBeto = json(enviar(get("/api/calificaciones?materiaId=%d&estudianteId=%d"
                .formatted(programacion.getId(), beto.getId())), profe, null)).get(0).get("id").asLong();
        enviar(patch("/api/calificaciones/" + notaBeto), profe,
                "{\"primerParcial\":10,\"segundoParcial\":10,\"trabajoPractico\":15,\"examenFinal\":40}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value(75))
                .andExpect(jsonPath("$.calificacion").value(3));
        enviar(get("/api/asistencia/resumen?materiaId=" + programacion.getId()), profe, null)
                .andExpect(jsonPath("$.estudiantes[1].condicion").value("EXONERADO"))
                .andExpect(jsonPath("$.estudiantes[1].motivoExoneracion").value("Reposo médico"));
    }

    @Test
    void hastaDosExoneracionesPorSemestre() throws Exception {
        Materia tercera = materia("INF-103", informatica, programacion.getDocente());
        Materia cuarta = materia("INF-104", informatica, programacion.getDocente());
        for (Materia materia : new Materia[] {programacion, algoritmos, tercera, cuarta}) {
            enviar(post("/api/inscripciones"), admin, cuerpoInscripcion(materia, beto)).andExpect(status().isOk());
        }
        String plantilla = "{\"estudianteId\":%d,\"materiaId\":%d,\"motivo\":\"Trabajo\"}";
        enviar(post("/api/asistencia/exoneraciones"), admin, plantilla.formatted(beto.getId(), programacion.getId()))
                .andExpect(status().isCreated());
        enviar(post("/api/asistencia/exoneraciones"), admin, plantilla.formatted(beto.getId(), tercera.getId()))
                .andExpect(status().isCreated());
        enviar(post("/api/asistencia/exoneraciones"), admin, plantilla.formatted(beto.getId(), cuarta.getId()))
                .andExpect(status().isConflict());
        // Solo puede exonerarse quien cursa la materia
        enviar(post("/api/asistencia/exoneraciones"), admin, plantilla.formatted(ana.getId(), cuarta.getId()))
                .andExpect(status().isConflict());

        enviar(get("/api/asistencia/exoneraciones"), admin, null).andExpect(jsonPath("$.length()").value(2));
        enviar(get("/api/asistencia/exoneraciones"), login("otro"), null).andExpect(jsonPath("$.length()").value(0));
    }

    // ======================= Notas desglosadas =======================

    @Test
    void laNotaDesglosadaEsLaSumaDeSusComponentes() throws Exception {
        String creada = enviar(post("/api/calificaciones"), profe, nota(ana, 10, 12, 15, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota").value(37))
                .andExpect(jsonPath("$.desglosada").value(true))
                .andExpect(jsonPath("$.calificacion").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(creada).get("id").asLong();

        // Cargar la primera nota inscribe al estudiante en la materia
        enviar(get("/api/inscripciones?materiaId=" + programacion.getId()), profe, null)
                .andExpect(jsonPath("$.length()").value(1));

        // Con los puntajes por defecto el 1er parcial vale 15
        enviar(post("/api/calificaciones"), profe, nota(beto, 16, null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("vale 15 puntos")));

        enviar(patch("/api/calificaciones/" + id), profe, "{\"nota\":95}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desglosada").value(false))
                .andExpect(jsonPath("$.primerParcial").doesNotExist())
                .andExpect(jsonPath("$.calificacion").value(5));
        enviar(patch("/api/calificaciones/" + id), profe, "{}").andExpect(status().isBadRequest());
    }

    // ======================= Puntajes por materia =======================

    @Test
    void elDocenteCambiaCuantoValeCadaComponente() throws Exception {
        enviar(get("/api/materias"), profe, null)
                .andExpect(jsonPath("$[0].puntajes.primerParcial").value(15))
                .andExpect(jsonPath("$[0].puntajes.examenFinal").value(50));

        enviar(put(rutaPuntajes(programacion)), profe, puntajes(20, 20, 0, 60))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.puntajes.primerParcial").value(20))
                .andExpect(jsonPath("$.puntajes.trabajoPractico").value(0))
                .andExpect(jsonPath("$.puntajes.examenFinal").value(60));

        // Ahora el parcial admite 20 y el TP ninguno
        enviar(post("/api/calificaciones"), profe, nota(ana, 20, 18, null, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nota").value(38))
                .andExpect(jsonPath("$.puntajes.segundoParcial").value(20));
        enviar(post("/api/calificaciones"), profe, nota(beto, null, null, 5, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("trabajo práctico vale 0")));
    }

    @Test
    void losPuntajesTienenQueSumar100() throws Exception {
        enviar(put(rutaPuntajes(programacion)), profe, puntajes(15, 15, 20, 40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("ahora suman 90")));
        // El examen final vale al menos 1 punto
        enviar(put(rutaPuntajes(programacion)), profe, puntajes(50, 30, 20, 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.examenFinal").exists());
    }

    @Test
    void noSeAchicaUnComponenteSiHayNotasQueLoSuperan() throws Exception {
        enviar(post("/api/calificaciones"), profe, nota(ana, 14, null, null, null)).andExpect(status().isCreated());

        enviar(put(rutaPuntajes(programacion)), profe, puntajes(10, 20, 20, 50))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("1 nota")));
        enviar(put(rutaPuntajes(programacion)), profe, puntajes(14, 16, 20, 50)).andExpect(status().isOk());
    }

    @Test
    void soloElDocenteACargoOUnAdminCambianLosPuntajes() throws Exception {
        enviar(put(rutaPuntajes(algoritmos)), profe, puntajes(20, 20, 20, 40)).andExpect(status().isForbidden());
        enviar(put(rutaPuntajes(algoritmos)), login("ana"), puntajes(20, 20, 20, 40)).andExpect(status().isForbidden());
        enviar(put(rutaPuntajes(algoritmos)), admin, puntajes(20, 20, 20, 40)).andExpect(status().isOk());
    }

    // ======================= Reporte y Excel =======================

    @Test
    void reporteYExcelDeHabilitados() throws Exception {
        inscribir(programacion, ana, beto);
        pasarLista(LocalDate.now().minusDays(1), "PRESENTE", "AUSENTE");
        pasarLista(LocalDate.now(), "TARDE", "AUSENTE");

        enviar(get("/api/asistencia/reporte"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.materias.length()").value(2))
                .andExpect(jsonPath("$.clases").value(2))
                .andExpect(jsonPath("$.noHabilitados").value(1))
                .andExpect(jsonPath("$.alertas[0].nombreEstudiante").value("Beto Ruiz"))
                .andExpect(jsonPath("$.carreras[0].nombre").value("Informática"));
        enviar(get("/api/asistencia/reporte"), login("otro"), null)
                .andExpect(jsonPath("$.materias.length()").value(1))
                .andExpect(jsonPath("$.materias[0].codigo").value("INF-102"));

        byte[] habilitados = enviar(get("/api/exportar/habilitados?materiaId=" + programacion.getId()), profe, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(habilitados))) {
            Sheet hoja = libro.getSheet("Habilitados");
            assertThat(texto(hoja, 3, 1)).isEqualTo("Ana Pérez");
            assertThat(texto(hoja, 3, 4)).isEqualTo("Habilitado");
            assertThat(texto(hoja, 4, 4)).isEqualTo("NO HABILITADO");
        }

        byte[] planilla = enviar(get("/api/exportar/asistencia?materiaId=" + programacion.getId()), profe, null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(planilla))) {
            assertThat(texto(libro.getSheet("Resumen"), 3, 0)).isEqualTo("Ana Pérez");
            Sheet clases = libro.getSheet("Clases");
            assertThat(texto(clases, 3, 2)).isEqualTo("P");
            assertThat(texto(clases, 3, 3)).isEqualTo("T");
            assertThat(texto(clases, 4, 3)).isEqualTo("A");
        }
        enviar(get("/api/exportar/habilitados?materiaId=" + programacion.getId()), login("otro"), null)
                .andExpect(status().isForbidden());
    }

    // ======================= Utilidades =======================

    private ResultActions inscribir(Materia materia, Estudiante... estudiantes) throws Exception {
        return enviar(post("/api/inscripciones"), profe, cuerpoInscripcion(materia, estudiantes));
    }

    private static String cuerpoInscripcion(Materia materia, Estudiante... estudiantes) {
        StringBuilder ids = new StringBuilder();
        for (Estudiante estudiante : estudiantes) {
            ids.append(ids.isEmpty() ? "" : ",").append(estudiante.getId());
        }
        return "{\"materiaId\":%d,\"estudianteIds\":[%s]}".formatted(materia.getId(), ids);
    }

    /** Lista de programación en la fecha indicada: estado de Ana y (si no es null) de Beto. */
    private ResultActions pasarLista(LocalDate fecha, String estadoAna, String estadoBeto) throws Exception {
        return enviar(post("/api/asistencia/clases"), profe, cuerpoClase(fecha, estadoAna, estadoBeto));
    }

    private String cuerpoClase(LocalDate fecha, String estadoAna, String estadoBeto) {
        String registros = "{\"estudianteId\":%d,\"estado\":\"%s\"}".formatted(ana.getId(), estadoAna);
        if (estadoBeto != null) {
            registros += ",{\"estudianteId\":%d,\"estado\":\"%s\"}".formatted(beto.getId(), estadoBeto);
        }
        return "{\"materiaId\":%d,\"fecha\":\"%s\",\"tema\":\"Clase\",\"registros\":[%s]}"
                .formatted(programacion.getId(), fecha, registros);
    }

    private String nota(Estudiante estudiante, Integer primero, Integer segundo, Integer practico, Integer examen) {
        return "{\"estudianteId\":%d,\"materiaId\":%d,\"primerParcial\":%s,\"segundoParcial\":%s,\"trabajoPractico\":%s,\"examenFinal\":%s}"
                .formatted(estudiante.getId(), programacion.getId(), primero, segundo, practico, examen);
    }

    private static String rutaPuntajes(Materia materia) {
        return "/api/materias/%d/puntajes".formatted(materia.getId());
    }

    private static String puntajes(int primero, int segundo, int practico, int examen) {
        return "{\"primerParcial\":%d,\"segundoParcial\":%d,\"trabajoPractico\":%d,\"examenFinal\":%d}"
                .formatted(primero, segundo, practico, examen);
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, String cuerpo) throws Exception {
        peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (cuerpo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
        }
        return mockMvc.perform(peticion);
    }

    private JsonNode json(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString());
    }

    private Materia materia(String codigo, Carrera carrera, Docente docente) {
        return materiaRepository.save(Materia.builder().codigo(codigo).nombre("Materia " + codigo).creditos(4)
                .semestre(1).carrera(carrera).docente(docente).build());
    }

    private Estudiante estudiante(String usuario, String matricula, String nombre, Carrera carrera) {
        return estudianteRepository.save(Estudiante.builder().usuario(crearUsuario(usuario, Rol.ESTUDIANTE))
                .matricula(matricula).nombreCompleto(nombre).carrera(carrera).build());
    }

    private Usuario crearUsuario(String username, Rol rol) {
        return usuarioRepository.save(Usuario.builder()
                .username(username).passwordHash(passwordEncoder.encode("secreto")).rol(rol).build());
    }

    private String login(String username) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"secreto\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("token").asText();
    }

    private static String texto(Sheet hoja, int fila, int columna) {
        var row = hoja.getRow(fila);
        return row == null ? "" : new DataFormatter().formatCellValue(row.getCell(columna)).trim();
    }
}
