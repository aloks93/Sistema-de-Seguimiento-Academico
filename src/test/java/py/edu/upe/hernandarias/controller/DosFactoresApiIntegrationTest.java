package py.edu.upe.hernandarias.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;
import py.edu.upe.hernandarias.security.LimiteIntentos;
import py.edu.upe.hernandarias.security.Totp;

/** Verificación en dos pasos con Google Authenticator: activación, login con código y recuperación. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DosFactoresApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired Totp totp;
    @Autowired LimiteIntentos limiteIntentos;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired DocenteRepository docenteRepository;
    @Autowired EstudianteRepository estudianteRepository;
    @Autowired MateriaRepository materiaRepository;
    @Autowired CarreraRepository carreraRepository;
    @Autowired CalificacionRepository calificacionRepository;

    @BeforeEach
    void prepararDatos() {
        limiteIntentos.limpiar("ana");
        calificacionRepository.deleteAll();
        estudianteRepository.deleteAll();
        materiaRepository.deleteAll();
        docenteRepository.deleteAll();
        carreraRepository.deleteAll();
        usuarioRepository.deleteAll();
        crearUsuario("ana", Rol.DOCENTE);
        crearUsuario("admin", Rol.ADMIN);
    }

    @Test
    void activarElSegundoPasoYDespuesElLoginPideElCodigo() throws Exception {
        String token = tokenDe(login("ana", "secreto", null));
        String secreto = iniciar(token);

        // Un código equivocado no lo activa
        mockMvc.perform(conToken(post("/api/perfil/2fa/activar"), token).content(codigo("000000")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/api/perfil"), token)).andExpect(jsonPath("$.dosFactores").value(false));

        mockMvc.perform(conToken(post("/api/perfil/2fa/activar"), token).content(codigo(codigoActual(secreto))))
                .andExpect(status().isNoContent());
        mockMvc.perform(conToken(get("/api/perfil"), token)).andExpect(jsonPath("$.dosFactores").value(true));

        // Sin código: la contraseña es correcta pero no hay token todavía
        login("ana", "secreto", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereCodigo").value(true))
                .andExpect(jsonPath("$.token").doesNotExist());
        // Contraseña incorrecta sigue siendo 401 aunque tenga 2FA
        login("ana", "mala", null).andExpect(status().isUnauthorized());
        // Código incorrecto
        login("ana", "secreto", "123456")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Código de verificación incorrecto"));
        // Código correcto
        login("ana", "secreto", codigoActual(secreto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.requiereCodigo").value(false));
    }

    @Test
    void elQrYLaClaveSirvenParaGoogleAuthenticator() throws Exception {
        String token = tokenDe(login("ana", "secreto", null));
        mockMvc.perform(conToken(post("/api/perfil/2fa/iniciar"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secreto").isNotEmpty())
                .andExpect(jsonPath("$.urlOtpauth").value(startsWith("otpauth://totp/")))
                .andExpect(jsonPath("$.qr").value(startsWith("data:image/svg+xml;base64,")))
                .andExpect(jsonPath("$.horaServidor").isNumber());
    }

    @Test
    void abrirElAsistenteOtraVezMuestraElMismoQr() throws Exception {
        String token = tokenDe(login("ana", "secreto", null));
        String primero = iniciar(token);
        String segundo = iniciar(token);
        org.assertj.core.api.Assertions.assertThat(segundo).isEqualTo(primero);

        // El código de la cuenta escaneada la primera vez sigue sirviendo
        mockMvc.perform(conToken(post("/api/perfil/2fa/activar"), token).content(codigo(codigoActual(primero))))
                .andExpect(status().isNoContent());
    }

    @Test
    void demasiadosCodigosIncorrectosBloqueanUnosMinutos() throws Exception {
        String token = tokenDe(login("ana", "secreto", null));
        String secreto = iniciar(token);
        mockMvc.perform(conToken(post("/api/perfil/2fa/activar"), token).content(codigo(codigoActual(secreto))))
                .andExpect(status().isNoContent());

        for (int i = 0; i < 5; i++) {
            login("ana", "secreto", "000000").andExpect(status().isUnauthorized());
        }
        // Ni siquiera el código correcto entra mientras dura el bloqueo
        login("ana", "secreto", codigoActual(secreto)).andExpect(status().isTooManyRequests());
    }

    @Test
    void desactivarExigeContraseniaYCodigo() throws Exception {
        String token = tokenDe(login("ana", "secreto", null));
        String secreto = iniciar(token);
        mockMvc.perform(conToken(post("/api/perfil/2fa/activar"), token).content(codigo(codigoActual(secreto))))
                .andExpect(status().isNoContent());

        mockMvc.perform(conToken(post("/api/perfil/2fa/desactivar"), token)
                        .content("{\"password\":\"mala\",\"codigo\":\"%s\"}".formatted(codigoActual(secreto))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(post("/api/perfil/2fa/desactivar"), token)
                        .content("{\"password\":\"secreto\",\"codigo\":\"%s\"}".formatted(codigoActual(secreto))))
                .andExpect(status().isNoContent());

        login("ana", "secreto", null).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void elAdminPuedeQuitarElSegundoPasoSiSePerdioElCelular() throws Exception {
        String token = tokenDe(login("ana", "secreto", null));
        String secreto = iniciar(token);
        mockMvc.perform(conToken(post("/api/perfil/2fa/activar"), token).content(codigo(codigoActual(secreto))))
                .andExpect(status().isNoContent());

        String tokenAdmin = tokenDe(login("admin", "secreto", null));
        long id = usuarioRepository.findByUsername("ana").orElseThrow().getId();
        mockMvc.perform(conToken(get("/api/usuarios"), tokenAdmin))
                .andExpect(jsonPath("$[?(@.username == 'ana')].dosFactores").value(true));
        mockMvc.perform(conToken(patch("/api/usuarios/" + id), tokenAdmin).content("{\"quitarDosFactores\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dosFactores").value(false));

        login("ana", "secreto", null).andExpect(jsonPath("$.token").isNotEmpty());
    }

    private String iniciar(String token) throws Exception {
        String respuesta = mockMvc.perform(conToken(post("/api/perfil/2fa/iniciar"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(respuesta).get("secreto").asText();
    }

    private String codigoActual(String secreto) {
        return totp.codigoEn(secreto, Instant.now().getEpochSecond());
    }

    private static String codigo(String codigo) {
        return "{\"codigo\":\"%s\"}".formatted(codigo);
    }

    private ResultActions login(String username, String password, String codigo) throws Exception {
        String cuerpo = codigo == null
                ? "{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)
                : "{\"username\":\"%s\",\"password\":\"%s\",\"codigo\":\"%s\"}".formatted(username, password, codigo);
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private String tokenDe(ResultActions login) throws Exception {
        JsonNode json = objectMapper.readTree(login.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return json.get("token").asText();
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private void crearUsuario(String username, Rol rol) {
        usuarioRepository.save(Usuario.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("secreto"))
                .rol(rol)
                .build());
    }
}
