package py.edu.upe.hernandarias.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.entity.Estudiante;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.entity.Usuario;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
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

    private final UsuarioRepository usuarioRepository;
    private final EstudianteRepository estudianteRepository;
    private final MateriaRepository materiaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioRepository.count() > 0) {
            return;
        }

        crearUsuario("admin", "admin123", Rol.ADMIN);
        crearUsuario("docente", "docente123", Rol.DOCENTE);
        Usuario usuarioEstudiante = crearUsuario("estudiante", "estudiante123", Rol.ESTUDIANTE);

        estudianteRepository.save(Estudiante.builder()
                .usuario(usuarioEstudiante)
                .matricula("2026-0001")
                .nombreCompleto("Estudiante de Prueba")
                .carrera("Ingeniería en Informática")
                .build());

        materiaRepository.save(Materia.builder().codigo("INF-101").nombre("Programación I").creditos(4).semestre(1).build());
        materiaRepository.save(Materia.builder().codigo("INF-102").nombre("Matemática Discreta").creditos(3).semestre(1).build());
        materiaRepository.save(Materia.builder().codigo("INF-201").nombre("Base de Datos I").creditos(4).semestre(2).build());

        log.info("Datos de ejemplo cargados (usuarios: admin, docente, estudiante)");
    }

    private Usuario crearUsuario(String username, String password, Rol rol) {
        return usuarioRepository.save(Usuario.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .rol(rol)
                .build());
    }
}
