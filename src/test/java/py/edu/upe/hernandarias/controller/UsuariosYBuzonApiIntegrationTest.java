package py.edu.upe.hernandarias.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.SolicitudRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;
import py.edu.upe.hernandarias.security.LimiteSolicitudes;

/** Jerarquía de administradores, eliminación con datos asociados y buzón de pedidos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuariosYBuzonApiIntegrationTest {

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
    @Autowired SolicitudRepository solicitudRepository;
    @Autowired LimiteSolicitudes limiteSolicitudes;

    String tokenJefe;

    @BeforeEach
    void prepararDatos() throws Exception {
        BaseDePrueba.vaciarAsistenciaEInscripciones(jdbcTemplate);
        calificacionRepository.deleteAll();
        estudianteRepository.deleteAll();
        materiaRepository.deleteAll();
        docenteRepository.deleteAll();
        carreraRepository.deleteAll();
        usuarioRepository.deleteAll();
        solicitudRepository.deleteAll();
        limiteSolicitudes.limpiar();

        usuarioRepository.save(Usuario.builder()
                .username("jefe")
                .passwordHash(passwordEncoder.encode("secreto"))
                .rol(Rol.ADMIN)
                .superAdmin(true)
                .build());
        tokenJefe = login("jefe", "secreto");
    }

    // ---------- Jerarquía de administradores ----------

    @Test
    void cualquierAdminCreaAdministradoresPeroSoloElSuperAdminLosModificaOElimina() throws Exception {
        crearAdmin(tokenJefe, "secretaria");
        String tokenSecretaria = login("secretaria", "clave123");
        // Un administrador común también crea administradores
        long auxiliar = crearAdmin(tokenSecretaria, "auxiliar");

        mockMvc.perform(conToken(delete("/api/usuarios/" + auxiliar), tokenSecretaria))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(patch("/api/usuarios/" + auxiliar), tokenSecretaria).content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(patch("/api/usuarios/" + auxiliar), tokenSecretaria).content("{\"password\":\"otra123\"}"))
                .andExpect(status().isForbidden());

        long jefe = idDe("jefe");
        mockMvc.perform(conToken(delete("/api/usuarios/" + jefe), tokenSecretaria))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(patch("/api/usuarios/" + jefe), tokenSecretaria).content("{\"activo\":false}"))
                .andExpect(status().isForbidden());
        // Nadie se elimina a sí mismo, ni siquiera el super administrador
        mockMvc.perform(conToken(delete("/api/usuarios/" + jefe), tokenJefe))
                .andExpect(status().isConflict());

        // El super administrador sí puede
        mockMvc.perform(conToken(patch("/api/usuarios/" + auxiliar), tokenJefe).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(delete("/api/usuarios/" + auxiliar), tokenJefe))
                .andExpect(status().isNoContent());
        assertThat(usuarioRepository.findByUsername("auxiliar")).isEmpty();

        mockMvc.perform(conToken(get("/api/perfil"), tokenJefe)).andExpect(jsonPath("$.superAdmin").value(true));
        mockMvc.perform(conToken(get("/api/perfil"), tokenSecretaria)).andExpect(jsonPath("$.superAdmin").value(false));
    }

    @Test
    void unAdminComunGestionaDocentesYEstudiantes() throws Exception {
        crearAdmin(tokenJefe, "secretaria");
        String tokenSecretaria = login("secretaria", "clave123");
        long carrera = crearCarrera();
        crearEstudiante("lucia", "2026-0001", carrera);

        long lucia = idDe("lucia");
        mockMvc.perform(conToken(patch("/api/usuarios/" + lucia), tokenSecretaria).content("{\"activo\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(delete("/api/usuarios/" + lucia), tokenSecretaria))
                .andExpect(status().isNoContent());
        assertThat(estudianteRepository.count()).isZero();
    }

    @Test
    void elListadoTraeElNombreYMarcaAlSuperAdmin() throws Exception {
        long carrera = crearCarrera();
        crearEstudiante("lucia", "2026-0001", carrera);

        mockMvc.perform(conToken(get("/api/usuarios"), tokenJefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'jefe')].superAdmin").value(true))
                .andExpect(jsonPath("$[?(@.username == 'lucia')].nombre").value("Estudiante lucia"))
                .andExpect(jsonPath("$[?(@.username == 'lucia')].detalle").value("2026-0001 · Ingeniería en Informática"));
    }

    // ---------- Eliminar con datos asociados ----------

    @Test
    void eliminarUnEstudianteConNotasPideConfirmarYDespuesBorraTodo() throws Exception {
        long carrera = crearCarrera();
        long docente = crearDocente("profe");
        long materia = crearMateria(carrera, docente);
        long estudiante = crearEstudiante("lucia", "2026-0001", carrera);
        mockMvc.perform(conToken(post("/api/calificaciones"), tokenJefe)
                        .content("{\"estudianteId\":%d,\"materiaId\":%d,\"nota\":70}".formatted(estudiante, materia)))
                .andExpect(status().isCreated());

        long usuario = idDe("lucia");
        mockMvc.perform(conToken(delete("/api/usuarios/" + usuario), tokenJefe))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.forzable").value(true));
        assertThat(estudianteRepository.count()).isOne();

        mockMvc.perform(conToken(delete("/api/usuarios/" + usuario + "?forzar=true"), tokenJefe))
                .andExpect(status().isNoContent());
        assertThat(estudianteRepository.count()).isZero();
        assertThat(calificacionRepository.count()).isZero();
        assertThat(usuarioRepository.findByUsername("lucia")).isEmpty();
    }

    @Test
    void eliminarUnDocenteConMateriasLasDejaSinAsignar() throws Exception {
        long carrera = crearCarrera();
        long docente = crearDocente("profe");
        long materia = crearMateria(carrera, docente);

        mockMvc.perform(conToken(delete("/api/docentes/" + docente), tokenJefe))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.forzable").value(true));
        mockMvc.perform(conToken(delete("/api/docentes/" + docente + "?forzar=true"), tokenJefe))
                .andExpect(status().isNoContent());

        assertThat(docenteRepository.count()).isZero();
        assertThat(materiaRepository.findById(materia).orElseThrow().getDocente()).isNull();
    }

    // ---------- Buzón ----------

    @Test
    void olvideMiContrasenaLlegaAlBuzonYElAdminLaRestablece() throws Exception {
        long carrera = crearCarrera();
        crearEstudiante("lucia", "2026-0001", carrera);

        enviarPedido("{\"tipo\":\"CONTRASENA\",\"username\":\"Lucia\",\"contacto\":\"lucia@correo.com\",\"mensaje\":\"No me acuerdo\"}")
                .andExpect(status().isAccepted());
        // Repetir el pedido no lo duplica
        enviarPedido("{\"tipo\":\"CONTRASENA\",\"username\":\"lucia\",\"mensaje\":\"Sigo sin poder entrar\"}")
                .andExpect(status().isAccepted());
        assertThat(solicitudRepository.count()).isOne();

        mockMvc.perform(conToken(get("/api/solicitudes/pendientes"), tokenJefe))
                .andExpect(jsonPath("$.cantidad").value(1));
        String lista = mockMvc.perform(conToken(get("/api/solicitudes?estado=PENDIENTE"), tokenJefe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioId").value(idDe("lucia")))
                .andExpect(jsonPath("$[0].usuarioRol").value("ESTUDIANTE"))
                .andExpect(jsonPath("$[0].mensaje").value("Sigo sin poder entrar"))
                .andReturn().getResponse().getContentAsString();
        long solicitud = objectMapper.readTree(lista).get(0).get("id").asLong();

        mockMvc.perform(conToken(post("/api/solicitudes/" + solicitud + "/restablecer"), tokenJefe)
                        .content("{\"password\":\"nueva123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESUELTA"))
                .andExpect(jsonPath("$.atendidaPor").value("jefe"));
        login("lucia", "nueva123");
        mockMvc.perform(conToken(get("/api/solicitudes/pendientes"), tokenJefe))
                .andExpect(jsonPath("$.cantidad").value(0));
    }

    @Test
    void elBuzonNoRevelaSiElUsuarioExisteYValidaLosDatos() throws Exception {
        enviarPedido("{\"tipo\":\"CONTRASENA\",\"username\":\"nadie\"}")
                .andExpect(status().isAccepted());
        enviarPedido("{\"tipo\":\"CONTRASENA\"}").andExpect(status().isBadRequest());
        enviarPedido("{\"tipo\":\"ACCESO\",\"contacto\":\"0981 000 000\"}").andExpect(status().isBadRequest());
        enviarPedido("{\"tipo\":\"ACCESO\",\"nombre\":\"Pedro Ruiz\",\"contacto\":\"0981 000 000\"}")
                .andExpect(status().isAccepted());

        mockMvc.perform(conToken(get("/api/solicitudes"), tokenJefe))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.username == 'nadie')].usuarioId", contains(nullValue())));
    }

    @Test
    void soloLosAdministradoresVenElBuzon() throws Exception {
        long carrera = crearCarrera();
        crearDocente("profe");
        crearEstudiante("lucia", "2026-0001", carrera);

        mockMvc.perform(get("/api/solicitudes")).andExpect(status().isUnauthorized());
        mockMvc.perform(conToken(get("/api/solicitudes"), login("profe", "clave123"))).andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/api/solicitudes"), login("lucia", "clave123"))).andExpect(status().isForbidden());
    }

    @Test
    void unAdminComunNoRestableceLaContrasenaDeOtroAdministrador() throws Exception {
        crearAdmin(tokenJefe, "secretaria");
        String tokenSecretaria = login("secretaria", "clave123");
        enviarPedido("{\"tipo\":\"CONTRASENA\",\"username\":\"jefe\"}").andExpect(status().isAccepted());
        long solicitud = solicitudRepository.findAll().get(0).getId();

        mockMvc.perform(conToken(post("/api/solicitudes/" + solicitud + "/restablecer"), tokenSecretaria)
                        .content("{\"password\":\"robada1\"}"))
                .andExpect(status().isForbidden());
        login("jefe", "secreto");
    }

    @Test
    void elEnvioMasivoDePedidosSeFrena() throws Exception {
        for (int i = 0; i < 5; i++) {
            enviarPedido("{\"tipo\":\"ACCESO\",\"nombre\":\"Persona %d\"}".formatted(i)).andExpect(status().isAccepted());
        }
        enviarPedido("{\"tipo\":\"ACCESO\",\"nombre\":\"Una más\"}").andExpect(status().isTooManyRequests());
    }

    // ---------- Utilidades ----------

    private ResultActions enviarPedido(String json) throws Exception {
        return mockMvc.perform(post("/api/solicitudes").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long crearAdmin(String token, String username) throws Exception {
        return id(mockMvc.perform(conToken(post("/api/usuarios"), token)
                        .content("{\"username\":\"%s\",\"password\":\"clave123\"}".formatted(username)))
                .andExpect(status().isCreated()));
    }

    private long crearCarrera() throws Exception {
        return id(mockMvc.perform(conToken(post("/api/carreras"), tokenJefe)
                        .content("{\"codigo\":\"INF\",\"nombre\":\"Ingeniería en Informática\",\"semestres\":10}"))
                .andExpect(status().isCreated()));
    }

    private long crearDocente(String username) throws Exception {
        return id(mockMvc.perform(conToken(post("/api/docentes"), tokenJefe)
                        .content("{\"username\":\"%s\",\"password\":\"clave123\",\"nombreCompleto\":\"Docente %s\"}"
                                .formatted(username, username)))
                .andExpect(status().isCreated()));
    }

    private long crearMateria(long carreraId, long docenteId) throws Exception {
        return id(mockMvc.perform(conToken(post("/api/materias"), tokenJefe)
                        .content("{\"codigo\":\"INF-101\",\"nombre\":\"Programación I\",\"creditos\":4,\"semestre\":1,"
                                + "\"carreraId\":%d,\"docenteId\":%d}".formatted(carreraId, docenteId)))
                .andExpect(status().isCreated()));
    }

    private long crearEstudiante(String username, String matricula, long carreraId) throws Exception {
        return id(mockMvc.perform(conToken(post("/api/estudiantes"), tokenJefe)
                        .content("""
                                {"username":"%s","password":"clave123","matricula":"%s",
                                 "nombreCompleto":"Estudiante %s","carreraId":%d}"""
                                .formatted(username, matricula, username, carreraId)))
                .andExpect(status().isCreated()));
    }

    private long idDe(String username) {
        return usuarioRepository.findByUsername(username).orElseThrow().getId();
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
