package py.edu.upe.hernandarias.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import py.edu.upe.hernandarias.entity.Rol;

/** API sin estado (stateless) protegida con JWT y control de acceso por roles (RBAC). */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Interfaz web estática (src/main/resources/static); los datos siguen protegidos por la API
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/css/**", "/js/**", "/favicon.ico")
                                .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // "Olvidé mi contraseña" y pedidos de acceso: se envían sin estar logueado
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes").permitAll()
                        .requestMatchers("/api/solicitudes/**").hasRole(Rol.ADMIN.name())
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/estudiantes/me/**", "/api/exportar/mis-notas").hasRole(Rol.ESTUDIANTE.name())
                        // Excel: el contenido respeta la jerarquía; docentes/carreras y la carga masiva son del ADMIN
                        .requestMatchers("/api/exportar/docentes", "/api/exportar/carreras", "/api/carga-masiva/**")
                                .hasRole(Rol.ADMIN.name())
                        .requestMatchers("/api/exportar/**").hasAnyRole(Rol.DOCENTE.name(), Rol.ADMIN.name())
                        // Las exoneraciones de asistencia las otorga y quita solo un ADMIN (los docentes las ven)
                        .requestMatchers(HttpMethod.POST, "/api/asistencia/exoneraciones/**").hasRole(Rol.ADMIN.name())
                        .requestMatchers(HttpMethod.DELETE, "/api/asistencia/exoneraciones/**").hasRole(Rol.ADMIN.name())
                        // Notas, asistencia e inscripciones: el servicio además limita a cada docente a sus materias
                        .requestMatchers("/api/calificaciones/**", "/api/asistencia/**", "/api/inscripciones/**")
                                .hasAnyRole(Rol.DOCENTE.name(), Rol.ADMIN.name())
                        // Los docentes dan de alta y editan estudiantes; solo el ADMIN los elimina
                        .requestMatchers(HttpMethod.DELETE, "/api/estudiantes/**").hasRole(Rol.ADMIN.name())
                        .requestMatchers("/api/estudiantes/**")
                                .hasAnyRole(Rol.DOCENTE.name(), Rol.ADMIN.name())
                        // Cuánto vale cada parcial, TP y final: el docente a cargo también (el servicio lo verifica)
                        .requestMatchers(HttpMethod.PUT, "/api/materias/*/puntajes")
                                .hasAnyRole(Rol.DOCENTE.name(), Rol.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/materias/**", "/api/carreras/**")
                                .hasAnyRole(Rol.DOCENTE.name(), Rol.ADMIN.name())
                        .requestMatchers("/api/materias/**", "/api/carreras/**", "/api/docentes/**",
                                        "/api/usuarios/**")
                                .hasRole(Rol.ADMIN.name())
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
