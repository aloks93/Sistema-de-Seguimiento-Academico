package py.edu.upe.hernandarias.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import py.edu.upe.hernandarias.BaseDePrueba;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

/** Carreras, docentes, materias, estudiantes y usuarios, y los permisos de cada rol. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GestionApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired DocenteRepository docenteRepository;
    @Autowired EstudianteRepository estudianteRepository;
    @Autowired CarreraRepository carreraRepository;
    @Autowired MateriaRepository materiaRepository;
    @Autowired CalificacionRepository calificacionRepository;

    String tokenAdmin;
    String tokenProfe;
    long informatica;
    long administracion;
    long profe;

    @BeforeEach
    void prepararDatos() throws Exception {
        BaseDePrueba.vaciarAsistenciaEInscripciones(jdbcTemplate);
        calificacionRepository.deleteAll();
        estudianteRepository.deleteAll();
        materiaRepository.deleteAll();
        docenteRepository.deleteAll();
        carreraRepository.deleteAll();
        usuarioRepository.deleteAll();

        usuarioRepository.save(Usuario.builder()
                .username("admin")
                .passwordHash(passwordEncoder.encode("secreto"))
                .rol(Rol.ADMIN)
                .build());
        tokenAdmin = login("admin", "secreto");

        informatica = crearCarrera("INF", "Ingeniería en Informática");
        administracion = crearCarrera("ADM", "Administración de Empresas");
        profe = crearDocente("profe", "Carlos Benítez");
        tokenProfe = login("profe", "clave123");
    }

    @Test
    void laInterfazWebSeSirveSinToken() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("js/app.js")));
        mockMvc.perform(get("/js/app.js")).andExpect(status().isOk());
        mockMvc.perform(get("/css/estilos.css")).andExpect(status().isOk());
    }

    @Test
    void adminGestionaCarrerasYDocenteSoloLasConsulta() throws Exception {
        mockMvc.perform(conToken(post("/api/carreras"), tokenAdmin)
                        .content("{\"codigo\":\"INF\",\"nombre\":\"Otra\",\"semestres\":8}"))
                .andExpect(status().isConflict());

        mockMvc.perform(conToken(put("/api/carreras/" + administracion), tokenAdmin)
                        .content("{\"codigo\":\"ADM\",\"nombre\":\"Lic. en Administración\",\"semestres\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Lic. en Administración"));

        crearMateria("INF-101", informatica, profe);
        mockMvc.perform(conToken(get("/api/carreras"), tokenAdmin))
                .andExpect(jsonPath("$.length()").value(2));
        // El docente solo ve las carreras donde tiene materias a cargo
        mockMvc.perform(conToken(get("/api/carreras"), tokenProfe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigo").value("INF"))
                .andExpect(jsonPath("$[0].materias").value(1));

        mockMvc.perform(conToken(post("/api/carreras"), tokenProfe)
                        .content("{\"codigo\":\"DER\",\"nombre\":\"Derecho\",\"semestres\":10}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(conToken(delete("/api/carreras/" + informatica), tokenAdmin))
                .andExpect(status().isConflict());
        mockMvc.perform(conToken(delete("/api/carreras/" + administracion), tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    void adminCreaDocentesYLesAsignaMaterias() throws Exception {
        crearMateria("INF-101", informatica, profe);
        crearMateria("INF-201", informatica, profe);

        mockMvc.perform(conToken(get("/api/docentes"), tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombreCompleto").value("Carlos Benítez"))
                .andExpect(jsonPath("$[0].materias.length()").value(2));

        crearMateria("INF-301", informatica, crearDocente("profe2", "Marta Ríos"));
        mockMvc.perform(conToken(get("/api/materias?carreraId=" + informatica), tokenProfe))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].docenteUsername").value("profe"))
                .andExpect(jsonPath("$[0].carreraCodigo").value("INF"));
        mockMvc.perform(conToken(get("/api/materias"), tokenAdmin))
                .andExpect(jsonPath("$.length()").value(3));

        mockMvc.perform(conToken(get("/api/docentes"), tokenProfe))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(post("/api/materias"), tokenProfe)
                        .content(materiaJson("INF-999", informatica, null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(conToken(delete("/api/docentes/" + profe), tokenAdmin))
                .andExpect(status().isConflict());
    }

    @Test
    void docenteSoloGestionaEstudiantesDeSusCarreras() throws Exception {
        crearMateria("INF-101", informatica, profe);
        long lucia = crearEstudiante(tokenProfe, "lucia", "2026-0100", informatica);
        long pedro = crearEstudiante(tokenAdmin, "pedro", "2026-0200", administracion);

        // No crea ni edita estudiantes de una carrera donde no enseña, ni pasa los suyos a otra
        mockMvc.perform(conToken(post("/api/estudiantes"), tokenProfe)
                        .content(estudianteJson("otro", "2026-0300", administracion)))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(put("/api/estudiantes/" + pedro), tokenProfe)
                        .content(estudianteUpdateJson("2026-0200", administracion, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(put("/api/estudiantes/" + lucia), tokenProfe)
                        .content(estudianteUpdateJson("2026-0100", administracion, null)))
                .andExpect(status().isForbidden());

        // Solo ve los de sus carreras; el ADMIN ve todos
        mockMvc.perform(conToken(get("/api/estudiantes"), tokenProfe))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].username").value("lucia"));
        mockMvc.perform(conToken(get("/api/estudiantes"), tokenAdmin))
                .andExpect(jsonPath("$.length()").value(2));

        // Edita los suyos (incluida la contraseña) pero no los elimina
        mockMvc.perform(conToken(put("/api/estudiantes/" + lucia), tokenProfe)
                        .content(estudianteUpdateJson("2026-0101", informatica, "nueva123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("2026-0101"))
                .andExpect(jsonPath("$.carreraNombre").value("Ingeniería en Informática"));
        login("lucia", "nueva123");

        mockMvc.perform(conToken(post("/api/estudiantes"), tokenProfe)
                        .content(estudianteJson("otro", "2026-0101", informatica)))
                .andExpect(status().isConflict());

        mockMvc.perform(conToken(delete("/api/estudiantes/" + lucia), tokenProfe))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(delete("/api/estudiantes/" + lucia), tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    void docenteSinMateriasNoVeNiGestionaNada() throws Exception {
        crearMateria("INF-101", informatica, crearDocente("profe2", "Marta Ríos"));
        crearEstudiante(tokenAdmin, "lucia", "2026-0100", informatica);

        mockMvc.perform(conToken(get("/api/carreras"), tokenProfe)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(conToken(get("/api/materias"), tokenProfe)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(conToken(get("/api/estudiantes"), tokenProfe)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(conToken(post("/api/estudiantes"), tokenProfe)
                        .content(estudianteJson("otro", "2026-0300", informatica)))
                .andExpect(status().isForbidden());
    }

    @Test
    void docenteSoloCalificaLasMateriasASuCargo() throws Exception {
        long otroProfe = crearDocente("profe2", "Marta Ríos");
        long propia = crearMateria("INF-101", informatica, profe);
        long ajena = crearMateria("INF-102", informatica, otroProfe);
        long sinDocente = crearMateria("INF-201", informatica, null);
        long lucia = crearEstudiante(tokenAdmin, "lucia", "2026-0100", informatica);

        nota(tokenProfe, lucia, propia, 80).andExpect(status().isCreated());
        nota(tokenProfe, lucia, ajena, 80).andExpect(status().isForbidden());
        nota(tokenProfe, lucia, sinDocente, 80).andExpect(status().isForbidden());

        long notaAjena = id(nota(tokenAdmin, lucia, ajena, 70).andExpect(status().isCreated()));
        nota(tokenAdmin, lucia, sinDocente, 60).andExpect(status().isCreated());

        mockMvc.perform(conToken(patch("/api/calificaciones/" + notaAjena), tokenProfe).content("{\"nota\":100}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(delete("/api/calificaciones/" + notaAjena), tokenProfe))
                .andExpect(status().isForbidden());

        mockMvc.perform(conToken(get("/api/calificaciones"), tokenProfe))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigoMateria").value("INF-101"))
                .andExpect(jsonPath("$[0].docenteNombre").value("Carlos Benítez"));
        mockMvc.perform(conToken(get("/api/calificaciones"), tokenAdmin))
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void noSeCalificaUnaMateriaDeOtraCarrera() throws Exception {
        long contabilidad = crearMateria("ADM-101", administracion, profe);
        long lucia = crearEstudiante(tokenAdmin, "lucia", "2026-0100", informatica);

        nota(tokenProfe, lucia, contabilidad, 90)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("no pertenece a la carrera")));
    }

    @Test
    void lasNotasProtegenEstudiantesMateriasYCambiosDeCarrera() throws Exception {
        long lucia = crearEstudiante(tokenAdmin, "lucia", "2026-0100", informatica);
        long materia = crearMateria("INF-101", informatica, profe);
        long notaId = id(nota(tokenProfe, lucia, materia, 60).andExpect(status().isCreated()));

        mockMvc.perform(conToken(delete("/api/estudiantes/" + lucia), tokenAdmin))
                .andExpect(status().isConflict());
        mockMvc.perform(conToken(delete("/api/materias/" + materia), tokenAdmin))
                .andExpect(status().isConflict());
        mockMvc.perform(conToken(put("/api/materias/" + materia), tokenAdmin)
                        .content(materiaJson("INF-101", administracion, profe)))
                .andExpect(status().isConflict());
        mockMvc.perform(conToken(put("/api/estudiantes/" + lucia), tokenAdmin)
                        .content("""
                                {"matricula":"2026-0100","nombreCompleto":"Lucía",
                                 "carreraId":%d}""".formatted(administracion)))
                .andExpect(status().isConflict());

        mockMvc.perform(conToken(delete("/api/calificaciones/" + notaId), tokenProfe))
                .andExpect(status().isNoContent());
        mockMvc.perform(conToken(delete("/api/estudiantes/" + lucia), tokenAdmin))
                .andExpect(status().isNoContent());
        mockMvc.perform(conToken(delete("/api/materias/" + materia), tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    void lasNotasSeFiltranPorEstudianteYMateria() throws Exception {
        long ana = crearEstudiante(tokenAdmin, "ana", "2026-0001", informatica);
        long beto = crearEstudiante(tokenAdmin, "beto", "2026-0002", informatica);
        long prog = crearMateria("INF-101", informatica, profe);
        long base = crearMateria("INF-201", informatica, profe);
        for (long[] par : new long[][] {{ana, prog}, {ana, base}, {beto, prog}}) {
            nota(tokenProfe, par[0], par[1], 80).andExpect(status().isCreated());
        }

        mockMvc.perform(conToken(get("/api/calificaciones?estudianteId=" + ana), tokenProfe))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(conToken(get("/api/calificaciones?materiaId=" + prog), tokenProfe))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(conToken(get("/api/calificaciones?estudianteId=%d&materiaId=%d".formatted(beto, prog)),
                        tokenProfe))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void adminCreaAdministradoresYDesactivaUsuarios() throws Exception {
        mockMvc.perform(conToken(post("/api/usuarios"), tokenAdmin)
                        .content("{\"username\":\"director\",\"password\":\"director1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("ADMIN"));
        login("director", "director1");

        long usuarioProfe = usuarioRepository.findByUsername("profe").orElseThrow().getId();
        mockMvc.perform(conToken(patch("/api/usuarios/" + usuarioProfe), tokenAdmin).content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"profe\",\"password\":\"clave123\"}"))
                .andExpect(status().isUnauthorized());

        long idAdmin = usuarioRepository.findByUsername("admin").orElseThrow().getId();
        mockMvc.perform(conToken(patch("/api/usuarios/" + idAdmin), tokenAdmin).content("{\"activo\":false}"))
                .andExpect(status().isConflict());
    }

    private long crearCarrera(String codigo, String nombre) throws Exception {
        return id(mockMvc.perform(conToken(post("/api/carreras"), tokenAdmin)
                        .content("{\"codigo\":\"%s\",\"nombre\":\"%s\",\"semestres\":10}".formatted(codigo, nombre)))
                .andExpect(status().isCreated()));
    }

    private long crearDocente(String username, String nombre) throws Exception {
        return id(mockMvc.perform(conToken(post("/api/docentes"), tokenAdmin)
                        .content("{\"username\":\"%s\",\"password\":\"clave123\",\"nombreCompleto\":\"%s\"}"
                                .formatted(username, nombre)))
                .andExpect(status().isCreated()));
    }

    private long crearMateria(String codigo, long carreraId, Long docenteId) throws Exception {
        return id(mockMvc.perform(conToken(post("/api/materias"), tokenAdmin)
                        .content(materiaJson(codigo, carreraId, docenteId)))
                .andExpect(status().isCreated()));
    }

    private long crearEstudiante(String token, String username, String matricula, long carreraId) throws Exception {
        return id(mockMvc.perform(conToken(post("/api/estudiantes"), token)
                        .content(estudianteJson(username, matricula, carreraId)))
                .andExpect(status().isCreated()));
    }

    private ResultActions nota(String token, long estudianteId, long materiaId, int nota) throws Exception {
        return mockMvc.perform(conToken(post("/api/calificaciones"), token)
                .content("{\"estudianteId\":%d,\"materiaId\":%d,\"nota\":%d}".formatted(estudianteId, materiaId, nota)));
    }

    private static String materiaJson(String codigo, long carreraId, Long docenteId) {
        return "{\"codigo\":\"%s\",\"nombre\":\"Materia %s\",\"creditos\":4,\"semestre\":3,\"carreraId\":%d,\"docenteId\":%s}"
                .formatted(codigo, codigo, carreraId, docenteId);
    }

    private static String estudianteJson(String username, String matricula, long carreraId) {
        return """
                {"username":"%s","password":"clave123","matricula":"%s",
                 "nombreCompleto":"Estudiante %s","carreraId":%d}""".formatted(username, matricula, username, carreraId);
    }

    private static String estudianteUpdateJson(String matricula, long carreraId, String password) {
        return """
                {"matricula":"%s","nombreCompleto":"Estudiante %s","carreraId":%d,"password":%s}"""
                .formatted(matricula, matricula, carreraId, password == null ? "null" : "\"" + password + "\"");
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private long id(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("token").asText();
    }
}
