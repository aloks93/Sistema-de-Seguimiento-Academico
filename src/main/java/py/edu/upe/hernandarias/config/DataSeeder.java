package py.edu.upe.hernandarias.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.entity.Asistencia;
import py.edu.upe.hernandarias.entity.Calificacion;
import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.entity.Clase;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.EstadoAsistencia;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Exoneracion;
import py.edu.upe.hernandarias.entity.Inscripcion;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.ClaseRepository;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.ExoneracionRepository;
import py.edu.upe.hernandarias.repository.InscripcionRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

/**
 * Carga datos de ejemplo cuando {@code app.seed.enabled=true} (activo en el perfil dev)
 * y la base de datos está vacía. Las credenciales están documentadas en el README.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String CLAVE_ESTUDIANTES = "estudiante123";

    private final UsuarioRepository usuarioRepository;
    private final DocenteRepository docenteRepository;
    private final EstudianteRepository estudianteRepository;
    private final CarreraRepository carreraRepository;
    private final MateriaRepository materiaRepository;
    private final InscripcionRepository inscripcionRepository;
    private final ClaseRepository claseRepository;
    private final ExoneracionRepository exoneracionRepository;
    private final CalificacionRepository calificacionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        Carrera informatica = carreraRepository.save(
                Carrera.builder().codigo("INF").nombre("Ingeniería en Informática").semestres(10).build());
        Carrera administracion = carreraRepository.save(
                Carrera.builder().codigo("ADM").nombre("Administración de Empresas").semestres(8).build());

        crearUsuario("admin", "admin123", Rol.ADMIN);
        Docente docente = docenteRepository.save(Docente.builder()
                .usuario(crearUsuario("docente", "docente123", Rol.DOCENTE))
                .nombreCompleto("Docente de Prueba")
                .build());

        Estudiante lucia = estudiante("lucia", "2026-0001", "Lucía Gómez", informatica);
        Estudiante marcos = estudiante("marcos", "2026-0002", "Marcos Rojas", informatica);
        Estudiante ana = estudiante("ana", "2026-0003", "Ana Benítez", informatica);
        Estudiante diego = estudiante("diego", "2026-0004", "Diego Fernández", informatica);
        Estudiante sofia = estudiante("sofia", "2026-0005", "Sofía Martínez", informatica);
        List<Estudiante> curso = List.of(lucia, marcos, ana, diego, sofia);

        Materia programacion = materiaRepository.save(Materia.builder().codigo("INF-101").nombre("Programación I")
                .creditos(4).semestre(1).carrera(informatica).docente(docente).clasesPrevistas(30).build());
        Materia discreta = materiaRepository.save(Materia.builder().codigo("INF-102").nombre("Matemática Discreta")
                .creditos(3).semestre(1).carrera(informatica).docente(docente).build());
        materiaRepository.save(Materia.builder().codigo("INF-201").nombre("Base de Datos I")
                .creditos(4).semestre(2).carrera(informatica).docente(docente).build());
        materiaRepository.save(Materia.builder().codigo("ADM-101").nombre("Contabilidad I")
                .creditos(4).semestre(1).carrera(administracion).build());

        for (Estudiante estudiante : curso) {
            inscripcionRepository.save(Inscripcion.builder().estudiante(estudiante).materia(programacion).build());
            inscripcionRepository.save(Inscripcion.builder().estudiante(estudiante).materia(discreta).build());
        }

        // Asistencia de ejemplo (P presente, T tarde, A ausente, J justificada), una letra por clase.
        // Con 30 clases previstas y 70 % de mínimo se permiten 9 ausencias en Programación I.
        registrarClases(programacion, Set.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), List.of(
                "Presentación de la materia", "Algoritmos y diagramas de flujo", "Variables y tipos de datos",
                "Operadores", "Estructuras condicionales", "Práctica: condicionales", "Ciclos while y for",
                "Práctica: ciclos", "Funciones", "Arreglos", "Repaso para el parcial", "Primer parcial"),
                Map.of(lucia, "PPPPPPPPPPPP",      // asistencia perfecta
                        marcos, "PAAPAPAAPAPA",     // 7 ausencias: en riesgo (le quedan 2)
                        ana, "AAPAAAAAPAAA",        // 10 ausencias: ya no llega al mínimo
                        diego, "PTPTPJPTPPPP",      // 3 tardanzas (= 1 ausencia) + 1 justificada
                        sofia, "PAJPAJPAJPAP"));    // 4 ausencias + 3 justificadas: en riesgo
        registrarClases(discreta, Set.of(DayOfWeek.TUESDAY), List.of(
                "Lógica proposicional", "Conjuntos", "Relaciones", "Funciones", "Inducción", "Combinatoria"),
                Map.of(lucia, "PPPPTP", marcos, "PPAPPP",
                        ana, "AAPAPA", diego, "PPPPPP", sofia, "PTPPPP"));
        exoneracionRepository.save(Exoneracion.builder().estudiante(ana).materia(discreta)
                .motivo("Reposo médico con certificado (ejemplo)").otorgadaPor("admin").build());

        // Notas desglosadas de Programación I: parciales y trabajo práctico; Lucía ya rindió el final
        nota(lucia, programacion, 14, 15, 19, 46);
        nota(marcos, programacion, 9, 10, 14, null);
        nota(ana, programacion, 7, null, 10, null);
        nota(diego, programacion, 11, 12, 16, null);
        nota(sofia, programacion, 13, 11, 17, null);

        log.info("Datos de ejemplo cargados (usuarios: admin, docente, lucia, marcos, ana, diego, sofia)");
    }

    private void registrarClases(Materia materia, Set<DayOfWeek> dias, List<String> temas,
                                 Map<Estudiante, String> asistencia) {
        List<LocalDate> fechas = new ArrayList<>();
        for (LocalDate dia = LocalDate.now().minusDays(1); fechas.size() < temas.size(); dia = dia.minusDays(1)) {
            if (dias.contains(dia.getDayOfWeek())) {
                fechas.add(dia);
            }
        }
        Collections.reverse(fechas);

        for (int i = 0; i < temas.size(); i++) {
            Clase clase = Clase.builder().materia(materia).fecha(fechas.get(i)).tema(temas.get(i)).build();
            for (Map.Entry<Estudiante, String> entrada : asistencia.entrySet()) {
                EstadoAsistencia estado = switch (entrada.getValue().charAt(i)) {
                    case 'T' -> EstadoAsistencia.TARDE;
                    case 'A' -> EstadoAsistencia.AUSENTE;
                    case 'J' -> EstadoAsistencia.JUSTIFICADO;
                    default -> EstadoAsistencia.PRESENTE;
                };
                clase.getAsistencias().add(Asistencia.builder()
                        .clase(clase)
                        .estudiante(entrada.getKey())
                        .estado(estado)
                        .observacion(estado == EstadoAsistencia.JUSTIFICADO ? "Certificado médico" : null)
                        .build());
            }
            claseRepository.save(clase);
        }
    }

    private void nota(Estudiante estudiante, Materia materia, Integer primerParcial, Integer segundoParcial,
                      Integer trabajoPractico, Integer examenFinal) {
        int total = (primerParcial == null ? 0 : primerParcial) + (segundoParcial == null ? 0 : segundoParcial)
                + (trabajoPractico == null ? 0 : trabajoPractico) + (examenFinal == null ? 0 : examenFinal);
        calificacionRepository.save(Calificacion.builder()
                .estudiante(estudiante).materia(materia).nota(total)
                .primerParcial(primerParcial).segundoParcial(segundoParcial)
                .trabajoPractico(trabajoPractico).examenFinal(examenFinal)
                .build());
    }

    private Estudiante estudiante(String usuario, String matricula, String nombre, Carrera carrera) {
        return estudianteRepository.save(Estudiante.builder()
                .usuario(crearUsuario(usuario, CLAVE_ESTUDIANTES, Rol.ESTUDIANTE))
                .matricula(matricula)
                .nombreCompleto(nombre)
                .carrera(carrera)
                .build());
    }

    private Usuario crearUsuario(String username, String password, Rol rol) {
        return usuarioRepository.save(Usuario.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .build());
    }
}
