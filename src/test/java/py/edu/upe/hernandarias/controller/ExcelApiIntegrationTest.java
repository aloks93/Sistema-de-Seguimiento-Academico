package py.edu.upe.hernandarias.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

/** Exportaciones a Excel, plantilla por carrera y carga masiva. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExcelApiIntegrationTest {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UsuarioRepository usuarioRepository;
    @Autowired DocenteRepository docenteRepository;
    @Autowired EstudianteRepository estudianteRepository;
    @Autowired MateriaRepository materiaRepository;
    @Autowired CarreraRepository carreraRepository;
    @Autowired CalificacionRepository calificacionRepository;

    String tokenAdmin;
    String tokenProfe;
    long informatica;
    long administracion;
    long programacion;
    long contabilidad;

    @BeforeEach
    void prepararDatos() throws Exception {
        calificacionRepository.deleteAll();
        estudianteRepository.deleteAll();
        materiaRepository.deleteAll();
        docenteRepository.deleteAll();
        carreraRepository.deleteAll();
        usuarioRepository.deleteAll();
        usuarioRepository.save(Usuario.builder().username("admin").passwordHash(passwordEncoder.encode("secreto")).rol(Rol.ADMIN).build());
        tokenAdmin = login("admin", "secreto");

        informatica = id(enviar(post("/api/carreras"), tokenAdmin, "{\"codigo\":\"INF\",\"nombre\":\"Informática\",\"semestres\":4}"));
        administracion = id(enviar(post("/api/carreras"), tokenAdmin, "{\"codigo\":\"ADM\",\"nombre\":\"Administración\",\"semestres\":4}"));
        long profe = id(enviar(post("/api/docentes"), tokenAdmin,
                "{\"username\":\"profe\",\"password\":\"clave123\",\"nombreCompleto\":\"Carlos Benítez\"}"));
        programacion = id(enviar(post("/api/materias"), tokenAdmin, materia("INF-101", informatica, profe, 1)));
        contabilidad = id(enviar(post("/api/materias"), tokenAdmin, materia("ADM-101", administracion, null, 1)));
        long lucia = id(enviar(post("/api/estudiantes"), tokenAdmin,
                "{\"username\":\"lucia\",\"password\":\"clave123\",\"matricula\":\"2026-0100\",\"nombreCompleto\":\"Lucía Gómez\",\"carreraId\":%d}"
                        .formatted(informatica)));
        tokenProfe = login("profe", "clave123");
        enviar(post("/api/calificaciones"), tokenProfe,
                "{\"estudianteId\":%d,\"materiaId\":%d,\"nota\":80}".formatted(lucia, programacion));
    }

    // ======================= Exportación =======================

    @Test
    void exportaEstudiantesComoExcel() throws Exception {
        byte[] archivo = mockMvc.perform(conToken(get("/api/exportar/estudiantes"), tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("estudiantes-")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            Sheet hoja = libro.getSheet("Estudiantes");
            assertThat(texto(hoja, 2, 0)).isEqualTo("Nombre completo");
            assertThat(texto(hoja, 3, 0)).isEqualTo("Lucía Gómez");
            assertThat(texto(hoja, 3, 2)).isEqualTo("Informática");
        }
    }

    @Test
    void elDocenteSoloExportaLoSuyo() throws Exception {
        byte[] planilla = mockMvc.perform(conToken(get("/api/exportar/notas?materiaId=" + programacion), tokenProfe))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(planilla))) {
            Sheet hoja = libro.getSheetAt(0);
            assertThat(texto(hoja, 3, 0)).isEqualTo("Lucía Gómez");
            assertThat(texto(hoja, 3, 2)).isEqualTo("80");
            assertThat(texto(hoja, 4, 0)).isEqualTo("Promedio");
        }

        mockMvc.perform(conToken(get("/api/exportar/notas?materiaId=" + contabilidad), tokenProfe))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/api/exportar/docentes"), tokenProfe)).andExpect(status().isForbidden());
        mockMvc.perform(conToken(get("/api/carga-masiva/plantilla?carreraId=" + informatica), tokenProfe))
                .andExpect(status().isForbidden());
    }

    @Test
    void elEstudianteDescargaSusNotas() throws Exception {
        String token = login("lucia", "clave123");
        byte[] archivo = mockMvc.perform(conToken(get("/api/exportar/mis-notas"), token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(archivo))) {
            assertThat(texto(libro.getSheetAt(0), 3, 1)).isEqualTo("INF-101");
        }
        mockMvc.perform(conToken(get("/api/exportar/estudiantes"), token)).andExpect(status().isForbidden());
    }

    // ======================= Semestres =======================

    @Test
    void lasMateriasRespetanLaDuracionDeLaCarrera() throws Exception {
        enviar(post("/api/materias"), tokenAdmin, materia("INF-901", informatica, null, 5))
                .andExpect(status().isBadRequest());
        enviar(post("/api/materias"), tokenAdmin, materia("INF-301", informatica, null, 3))
                .andExpect(status().isCreated());
        enviar(put("/api/carreras/" + informatica), tokenAdmin, "{\"codigo\":\"INF\",\"nombre\":\"Informática\",\"semestres\":2}")
                .andExpect(status().isConflict());
    }

    // ======================= Plantilla y carga masiva =======================

    @Test
    void laPlantillaTraeFilasParaCadaSemestreYLoYaRegistrado() throws Exception {
        try (XSSFWorkbook libro = plantilla(informatica)) {
            assertThat(libro.getSheetName(0)).isEqualTo("Instrucciones");
            assertThat(libro.getSheetVisibility(libro.getSheetIndex("datos"))).isEqualTo(SheetVisibility.VERY_HIDDEN);

            Sheet materias = libro.getSheet("Materias");
            assertThat(texto(materias, 3, 0)).isEqualTo("INF-101");   // ya registrada
            assertThat(texto(materias, 3, 4)).isEqualTo("profe");
            int ultima = materias.getLastRowNum();
            assertThat(ultima - 3).isEqualTo(4 * 6);                    // 4 semestres × 6 filas vacías
            assertThat(texto(materias, ultima, 2)).isEqualTo("4");
            assertThat(texto(libro.getSheet("Estudiantes"), 3, 1)).isEqualTo("2026-0100");
        }
    }

    @Test
    void cargaMasivaValidaPrimeroYDespuesImportaTodo() throws Exception {
        byte[] archivo;
        try (XSSFWorkbook libro = plantilla(informatica)) {
            escribirEnPrimeraFilaLibre(libro.getSheet("Docentes"), "Marta Ríos", "marta", "marta123");
            Row materia = primeraFilaLibre(libro.getSheet("Materias"));   // semestre 1 ya precargado
            materia.getCell(0).setCellValue("INF-102");
            materia.getCell(1).setCellValue("Algoritmos");
            materia.getCell(3).setCellValue(4);
            materia.getCell(4).setCellValue("marta");
            escribirEnPrimeraFilaLibre(libro.getSheet("Estudiantes"), "Pedro Gómez", "2026-0200", "pedro", "pedro123");
            archivo = aBytes(libro);
        }

        subir(archivo, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(false))
                .andExpect(jsonPath("$.errores").value(0))
                .andExpect(jsonPath("$.docentes").value(1))
                .andExpect(jsonPath("$.materias").value(1))
                .andExpect(jsonPath("$.estudiantes").value(1))
                .andExpect(jsonPath("$.omitidos").value(3));
        assertThat(materiaRepository.findByCodigo("INF-102")).isEmpty();

        subir(archivo, false)
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.carrera").value("Informática"));
        mockMvc.perform(conToken(get("/api/materias?carreraId=" + informatica), tokenAdmin))
                .andExpect(jsonPath("$[?(@.codigo == 'INF-102')].docenteNombre").value(org.hamcrest.Matchers.hasItem("Marta Ríos")));
        login("pedro", "pedro123");
        login("marta", "marta123");

        // Volver a subir la misma plantilla no duplica nada
        subir(archivo, false)
                .andExpect(jsonPath("$.aplicado").value(true))
                .andExpect(jsonPath("$.docentes").value(0))
                .andExpect(jsonPath("$.materias").value(0))
                .andExpect(jsonPath("$.estudiantes").value(0))
                .andExpect(jsonPath("$.omitidos").value(6));
    }

    @Test
    void conUnErrorNoSeGuardaNada() throws Exception {
        long docentesAntes = docenteRepository.count();
        byte[] archivo;
        try (XSSFWorkbook libro = plantilla(informatica)) {
            escribirEnPrimeraFilaLibre(libro.getSheet("Docentes"), "Marta Ríos", "marta", "marta123"); // fila válida
            Row materia = primeraFilaLibre(libro.getSheet("Materias"));
            materia.getCell(0).setCellValue("INF-999");
            materia.getCell(1).setCellValue("Fuera de plan");
            materia.getCell(2).setCellValue(9);              // la carrera dura 4 semestres
            materia.getCell(3).setCellValue(4);
            materia.getCell(4).setCellValue("nadie");        // docente inexistente
            escribirEnPrimeraFilaLibre(libro.getSheet("Estudiantes"), "Sin Clave", "2026-0300", "sinclave", "123");
            archivo = aBytes(libro);
        }

        subir(archivo, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicado").value(false))
                .andExpect(jsonPath("$.errores").value(2))
                .andExpect(jsonPath("$.observaciones[?(@.hoja == 'Materias' && @.tipo == 'ERROR')].mensaje")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("Semestre"), org.hamcrest.Matchers.containsString("nadie")))))
                .andExpect(jsonPath("$.observaciones[?(@.hoja == 'Estudiantes')].mensaje")
                        .value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("contraseña"))));

        assertThat(docenteRepository.count()).isEqualTo(docentesAntes);
        assertThat(usuarioRepository.existsByUsername("marta")).isFalse();
    }

    @Test
    void unArchivoQueNoEsPlantillaSeRechaza() throws Exception {
        byte[] otro;
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            libro.createSheet("Hoja1");
            otro = aBytes(libro);
        }
        subir(otro, true).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("plantilla")));
        subir("no soy un excel".getBytes(), true).andExpect(status().isBadRequest());
    }

    // ======================= Utilidades =======================

    private XSSFWorkbook plantilla(long carreraId) throws Exception {
        byte[] bytes = mockMvc.perform(conToken(get("/api/carga-masiva/plantilla?carreraId=" + carreraId), tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX))
                .andReturn().getResponse().getContentAsByteArray();
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    private ResultActions subir(byte[] contenido, boolean soloValidar) throws Exception {
        return mockMvc.perform(multipart("/api/carga-masiva")
                .file(new MockMultipartFile("archivo", "plantilla.xlsx", XLSX, contenido))
                .param("soloValidar", String.valueOf(soloValidar))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin));
    }

    private static Row primeraFilaLibre(Sheet hoja) {
        for (int i = 3; i <= hoja.getLastRowNum(); i++) {
            if (texto(hoja, i, 0).isEmpty() && texto(hoja, i, 1).isEmpty()) {
                return hoja.getRow(i);
            }
        }
        throw new IllegalStateException("No hay filas libres");
    }

    private static void escribirEnPrimeraFilaLibre(Sheet hoja, String... valores) {
        Row fila = primeraFilaLibre(hoja);
        for (int i = 0; i < valores.length; i++) {
            fila.getCell(i).setCellValue(valores[i]);
        }
    }

    private static String texto(Sheet hoja, int fila, int columna) {
        Row row = hoja.getRow(fila);
        return row == null ? "" : new DataFormatter().formatCellValue(row.getCell(columna)).trim();
    }

    private static byte[] aBytes(XSSFWorkbook libro) throws Exception {
        try (ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private static String materia(String codigo, long carreraId, Long docenteId, int semestre) {
        return "{\"codigo\":\"%s\",\"nombre\":\"Materia %s\",\"creditos\":4,\"semestre\":%d,\"carreraId\":%d,\"docenteId\":%s}"
                .formatted(codigo, codigo, semestre, carreraId, docenteId);
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, String token, String cuerpo) throws Exception {
        return mockMvc.perform(conToken(peticion, token).content(cuerpo));
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private long id(ResultActions resultado) throws Exception {
        return objectMapper.readTree(resultado.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
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
