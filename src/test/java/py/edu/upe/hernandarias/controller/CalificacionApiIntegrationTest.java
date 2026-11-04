package py.edu.upe.hernandarias.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class CalificacionApiIntegrationTest {

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

    Estudiante estudiante;
    Materia materia;

    @BeforeEach
    void prepararDatos() {
        BaseDePrueba.vaciarAsistenciaEInscripciones(jdbcTemplate);
        calificacionRepository.deleteAll();
        estudianteRepository.deleteAll();
        materiaRepository.deleteAll();
        docenteRepository.deleteAll();
        carreraRepository.deleteAll();
        usuarioRepository.deleteAll();

        Carrera carrera = carreraRepository.save(
                Carrera.builder().codigo("INF").nombre("Ingeniería en Informática").build());
        Docente docente = docenteRepository.save(Docente.builder()
                .usuario(crearUsuario("docente", Rol.DOCENTE, true))
                .nombreCompleto("Carlos Benítez")
                .build());
        crearUsuario("inactivo", Rol.DOCENTE, false);
        estudiante = estudianteRepository.save(Estudiante.builder()
                .usuario(crearUsuario("alumno", Rol.ESTUDIANTE, true))
                .matricula("2026-0001")
                .nombreCompleto("Ana Pérez")
                .carrera(carrera)
                .build());
        materia = materiaRepository.save(Materia.builder()
                .codigo("INF-101").nombre("Programación I").creditos(4).semestre(1)
                .carrera(carrera).docente(docente).build());
    }

    @Test
    void loginDevuelveJwtYLasContraseniasSeGuardanConBCrypt() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"docente\",\"password\":\"secreto\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.rol").value("DOCENTE"));

        String hash = usuarioRepository.findByUsername("docente").orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2a$");
    }

    @Test
    void loginRechazaCredencialesInvalidasYUsuariosInactivos() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"docente\",\"password\":\"incorrecta\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"inactivo\",\"password\":\"secreto\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenResponde401() throws Exception {
        mockMvc.perform(get("/api/estudiantes/me/calificaciones"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void docenteRegistraYActualizaNotaYEstudianteLaConsulta() throws Exception {
        String tokenDocente = login("docente");

        String creada = mockMvc.perform(post("/api/calificaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDocente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro(85)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.nota").value(85))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(creada).get("id").asLong();

        mockMvc.perform(patch("/api/calificaciones/" + id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDocente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":92}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nota").value(92));

        mockMvc.perform(get("/api/estudiantes/me/calificaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("alumno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].codigoMateria").value("INF-101"))
                .andExpect(jsonPath("$[0].nota").value(92));
    }

    @Test
    void estudianteNoPuedeRegistrarNotas() throws Exception {
        mockMvc.perform(post("/api/calificaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("alumno"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro(100)))
                .andExpect(status().isForbidden());
    }

    @Test
    void notaFueraDeRangoResponde400() throws Exception {
        mockMvc.perform(post("/api/calificaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("docente"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.nota").exists());
    }

    @Test
    void notaDuplicadaParaMismaMateriaResponde409() throws Exception {
        String token = login("docente");
        mockMvc.perform(post("/api/calificaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro(70)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/calificaciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoRegistro(80)))
                .andExpect(status().isConflict());
    }

    @Test
    void actualizarCalificacionInexistenteResponde404() throws Exception {
        mockMvc.perform(patch("/api/calificaciones/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + login("docente"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":50}"))
                .andExpect(status().isNotFound());
    }

    private Usuario crearUsuario(String username, Rol rol, boolean activo) {
        return usuarioRepository.save(Usuario.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("secreto"))
                .rol(rol)
                .activo(activo)
                .build());
    }

    private String login(String username) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"secreto\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(respuesta);
        return json.get("token").asText();
    }

    private String cuerpoRegistro(int nota) {
        return "{\"estudianteId\":%d,\"materiaId\":%d,\"nota\":%d}"
                .formatted(estudiante.getId(), materia.getId(), nota);
    }
}
