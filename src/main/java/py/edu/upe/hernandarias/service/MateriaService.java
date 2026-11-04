package py.edu.upe.hernandarias.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.dto.MateriaRequest;
import py.edu.upe.hernandarias.dto.MateriaResponse;
import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.DatoInvalidoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.ClaseRepository;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.ExoneracionRepository;
import py.edu.upe.hernandarias.repository.InscripcionRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;

@Service
@RequiredArgsConstructor
public class MateriaService {

    private final MateriaRepository materiaRepository;
    private final CarreraRepository carreraRepository;
    private final DocenteRepository docenteRepository;
    private final CalificacionRepository calificacionRepository;
    private final InscripcionRepository inscripcionRepository;
    private final ClaseRepository claseRepository;
    private final ExoneracionRepository exoneracionRepository;
    private final PermisosService permisosService;

    /**
     * Para un ADMIN, todas las materias (o las de una carrera si {@code carreraId} no es null);
     * para un docente, solo las que tiene a cargo.
     */
    @Transactional(readOnly = true)
    public List<MateriaResponse> listar(Long carreraId, String username) {
        List<Materia> materias;
        if (!permisosService.esAdmin(username)) {
            materias = materiaRepository.findByDocenteUsuarioUsernameOrderBySemestreAscNombreAsc(username).stream()
                    .filter(materia -> carreraId == null || materia.getCarrera().getId().equals(carreraId))
                    .toList();
        } else if (carreraId == null) {
            materias = materiaRepository.findAllByOrderBySemestreAscNombreAsc();
        } else {
            materias = materiaRepository.findByCarreraIdOrderBySemestreAscNombreAsc(carreraId);
        }
        Map<Long, Long> inscriptos = inscripcionRepository.contarPorMateria().stream()
                .collect(Collectors.toMap(fila -> (Long) fila[0], fila -> (Long) fila[1]));
        return materias.stream()
                .map(materia -> MateriaResponse.from(materia, inscriptos.getOrDefault(materia.getId(), 0L)))
                .toList();
    }

    @Transactional
    public MateriaResponse crear(MateriaRequest request) {
        String codigo = request.codigo().trim();
        if (materiaRepository.existsByCodigo(codigo)) {
            throw new ConflictoException("Ya existe una materia con el código " + codigo);
        }

        Carrera carrera = buscarCarrera(request.carreraId());
        verificarSemestre(request.semestre(), carrera);

        Materia materia = Materia.builder()
                .codigo(codigo)
                .nombre(request.nombre().trim())
                .creditos(request.creditos())
                .semestre(request.semestre())
                .carrera(carrera)
                .docente(buscarDocente(request.docenteId()))
                .asistenciaMinima(request.asistenciaMinima())
                .clasesPrevistas(request.clasesPrevistas())
                .build();

        return MateriaResponse.from(materiaRepository.save(materia), 0);
    }

    @Transactional
    public MateriaResponse actualizar(Long id, MateriaRequest request) {
        Materia materia = buscar(id);
        String codigo = request.codigo().trim();
        if (materiaRepository.existsByCodigoAndIdNot(codigo, id)) {
            throw new ConflictoException("Ya existe otra materia con el código " + codigo);
        }
        Carrera carrera = buscarCarrera(request.carreraId());
        verificarSemestre(request.semestre(), carrera);
        if (!carrera.getId().equals(materia.getCarrera().getId()) && (calificacionRepository.existsByMateriaId(id)
                || inscripcionRepository.existsByMateriaId(id) || claseRepository.existsByMateriaId(id))) {
            throw new ConflictoException("La materia %s ya tiene inscriptos, notas o clases; no se puede cambiar de carrera"
                    .formatted(materia.getCodigo()));
        }

        materia.setCodigo(codigo);
        materia.setNombre(request.nombre().trim());
        materia.setCreditos(request.creditos());
        materia.setSemestre(request.semestre());
        materia.setCarrera(carrera);
        materia.setDocente(buscarDocente(request.docenteId()));
        materia.setAsistenciaMinima(request.asistenciaMinima());
        materia.setClasesPrevistas(request.clasesPrevistas());

        return MateriaResponse.from(materiaRepository.save(materia), inscripcionRepository.countByMateriaId(id));
    }

    /**
     * Solo se eliminan materias sin notas ni clases, para no perder datos por accidente.
     * Sus inscripciones y exoneraciones se borran con ella.
     */
    @Transactional
    public void eliminar(Long id) {
        Materia materia = buscar(id);
        if (calificacionRepository.existsByMateriaId(id) || claseRepository.existsByMateriaId(id)) {
            throw new ConflictoException("La materia %s tiene notas o clases registradas; elimínelas primero"
                    .formatted(materia.getCodigo()));
        }
        exoneracionRepository.eliminarDeLaMateria(id);
        inscripcionRepository.eliminarDeLaMateria(id);
        materiaRepository.delete(materia);
    }

    private static void verificarSemestre(int semestre, Carrera carrera) {
        if (semestre > carrera.getSemestres()) {
            throw new DatoInvalidoException("%s dura %d semestres; la materia no puede estar en el semestre %d"
                    .formatted(carrera.getNombre(), carrera.getSemestres(), semestre));
        }
    }

    private Materia buscar(Long id) {
        return materiaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Materia", id));
    }

    private Carrera buscarCarrera(Long id) {
        return carreraRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Carrera", id));
    }

    private Docente buscarDocente(Long id) {
        if (id == null) {
            return null;
        }
        return docenteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Docente", id));
    }
}
