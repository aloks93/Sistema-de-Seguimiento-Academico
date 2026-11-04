package py.edu.upe.hernandarias.service;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.entity.Rol;
import py.edu.upe.hernandarias.exception.OperacionNoPermitidaException;
import py.edu.upe.hernandarias.repository.MateriaRepository;
import py.edu.upe.hernandarias.repository.UsuarioRepository;

/**
 * Jerarquía de acceso: un ADMIN opera sobre todo; un DOCENTE solo sobre las carreras
 * en las que tiene al menos una materia a cargo (y sus estudiantes).
 */
@Service
@RequiredArgsConstructor
public class PermisosService {

    private final UsuarioRepository usuarioRepository;
    private final MateriaRepository materiaRepository;

    @Transactional(readOnly = true)
    public boolean esAdmin(String username) {
        return usuarioRepository.findByUsername(username)
                .map(usuario -> usuario.getRol() == Rol.ADMIN)
                .orElse(false);
    }

    /** Carreras donde el docente tiene materias a cargo. */
    @Transactional(readOnly = true)
    public Set<Long> carrerasDelDocente(String username) {
        return materiaRepository.findCarreraIdsByDocenteUsername(username);
    }

    /** Lanza 403 si el usuario no es ADMIN y la carrera no es una de las suyas. */
    @Transactional(readOnly = true)
    public void verificarCarrera(Long carreraId, String username) {
        if (!esAdmin(username) && !carrerasDelDocente(username).contains(carreraId)) {
            throw new OperacionNoPermitidaException(
                    "Solo puede gestionar estudiantes de las carreras donde tiene materias a cargo");
        }
    }

    /** Lanza 403 si el usuario no es ADMIN ni el docente a cargo de la materia. */
    @Transactional(readOnly = true)
    public void verificarMateria(Materia materia, String username) {
        if (esAdmin(username)) {
            return;
        }
        Docente docente = materia.getDocente();
        if (docente == null || !docente.getUsuario().getUsername().equals(username)) {
            throw new OperacionNoPermitidaException(
                    "Solo el docente a cargo de %s puede gestionarla".formatted(materia.getCodigo()));
        }
    }
}
