package py.edu.upe.hernandarias.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

/** Perfil del usuario autenticado: nombre, preferencias de apariencia y cambio de contraseña. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PerfilApiIntegrationTest {

    private static final String PREFERENCIAS = """
            {"modo":"claro","colorPrimario":"#10b981","colorBarra":"#0b1220",
             "densidad":"compacta","tamanoTexto":"grande"}""";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired DocenteRepository docenteRepository;
    @Autowired EstudianteRepository estudianteRepository;
    @Autowired MateriaRepository materiaRepository;
    @Autowired CarreraRepository carreraRepository;
    @Autowired CalificacionRepository calificacionRepository;

    String token;

    @BeforeEach
    void prepararDatos() throws Exception {
        calificacionRepository.deleteAll();
        estudianteRepository.deleteAll();
        materiaRepository.deleteAll();
        docenteRepository.deleteAll();
        carreraRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .username("profe")
                .passwordHash(passwordEncoder.encode("secreto"))
                .rol(Rol.DOCENTE)
                .build());
        docenteRepository.save(Docente.builder().usuario(usuario).nombreCompleto("Carlos Benítez").build());
        token = login("profe", "secreto");
    }

    @Test
    void perfilMuestraNombreYSinPreferenciasUsaLasDePorDefecto() throws Exception {
        mockMvc.perform(conToken(get("/api/perfil")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("profe"))
                .andExpect(jsonPath("$.rol").value("DOCENTE"))
                .andExpect(jsonPath("$.nombre").value("Carlos Benítez"))
                .andExpect(jsonPath("$.preferencias").doesNotExist());
    }

    @Test
    void lasPreferenciasSeGuardanPorUsuario() throws Exception {
        mockMvc.perform(conToken(put("/api/perfil/preferencias")).content(PREFERENCIAS))
                .andExpect(status().isOk());

        mockMvc.perform(conToken(get("/api/perfil")))
                .andExpect(jsonPath("$.preferencias.modo").value("claro"))
                .andExpect(jsonPath("$.preferencias.colorPrimario").value("#10b981"))
                .andExpect(jsonPath("$.preferencias.densidad").value("compacta"));
    }

    @Test
    void preferenciasInvalidasResponden400() throws Exception {
        mockMvc.perform(conToken(put("/api/perfil/preferencias"))
                        .content(PREFERENCIAS.replace("#10b981", "verde")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.colorPrimario").exists());
    }

    @Test
    void cambiarLaPropiaContrasenia() throws Exception {
        mockMvc.perform(conToken(put("/api/perfil/contrasena"))
                        .content("{\"actual\":\"incorrecta\",\"nueva\":\"nueva123\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(conToken(put("/api/perfil/contrasena"))
                        .content("{\"actual\":\"secreto\",\"nueva\":\"nueva123\"}"))
                .andExpect(status().isNoContent());
        login("profe", "nueva123");
    }

    @Test
    void sinTokenNoHayPerfil() throws Exception {
        mockMvc.perform(get("/api/perfil")).andExpect(status().isUnauthorized());
    }

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
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
