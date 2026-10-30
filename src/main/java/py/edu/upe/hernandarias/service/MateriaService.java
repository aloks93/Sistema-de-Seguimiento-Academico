package py.edu.upe.hernandarias.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.dto.MateriaRequest;
import py.edu.upe.hernandarias.dto.MateriaResponse;
import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.entity.Docente;
import py.edu.upe.hernandarias.entity.Materia;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.CalificacionRepository;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.DocenteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;

@Service
@RequiredArgsConstructor
public class MateriaService {

    private final MateriaRepository materiaRepository;
    private final CarreraRepository carreraRepository;
    private final DocenteRepository docenteRepository;
    private final CalificacionRepository calificacionRepository;

    /** Todas las materias, o solo las de una carrera si {@code carreraId} no es null. */
    @Transactional(readOnly = true)
    public List<MateriaResponse> listar(Long carreraId) {
        List<Materia> materias = carreraId == null
                ? materiaRepository.findAllByOrderBySemestreAscNombreAsc()
                : materiaRepository.findByCarreraIdOrderBySemestreAscNombreAsc(carreraId);
        return materias.stream().map(MateriaResponse::from).toList();
    }

    @Transactional
    public MateriaResponse crear(MateriaRequest request) {
        String codigo = request.codigo().trim();
        if (materiaRepository.existsByCodigo(codigo)) {
            throw new ConflictoException("Ya existe una materia con el código " + codigo);
        }

        Materia materia = Materia.builder()
                .codigo(codigo)
                .nombre(request.nombre().trim())
                .creditos(request.creditos())
                .semestre(request.semestre())
                .carrera(buscarCarrera(request.carreraId()))
                .docente(buscarDocente(request.docenteId()))
                .build();

        return MateriaResponse.from(materiaRepository.save(materia));
    }

    @Transactional
    public MateriaResponse actualizar(Long id, MateriaRequest request) {
        Materia materia = buscar(id);
        String codigo = request.codigo().trim();
        if (materiaRepository.existsByCodigoAndIdNot(codigo, id)) {
            throw new ConflictoException("Ya existe otra materia con el código " + codigo);
        }
        Carrera carrera = buscarCarrera(request.carreraId());
        if (!carrera.getId().equals(materia.getCarrera().getId()) && calificacionRepository.existsByMateriaId(id)) {
            throw new ConflictoException(
                    "La materia %s ya tiene notas; no se puede cambiar de carrera".formatted(materia.getCodigo()));
        }

        materia.setCodigo(codigo);
        materia.setNombre(request.nombre().trim());
        materia.setCreditos(request.creditos());
        materia.setSemestre(request.semestre());
        materia.setCarrera(carrera);
        materia.setDocente(buscarDocente(request.docenteId()));

        return MateriaResponse.from(materiaRepository.save(materia));
    }

    /** Solo se eliminan materias sin notas, para no perder calificaciones por accidente. */
    @Transactional
    public void eliminar(Long id) {
        Materia materia = buscar(id);
        if (calificacionRepository.existsByMateriaId(id)) {
            throw new ConflictoException(
                    "La materia %s tiene notas cargadas; elimine primero esas notas".formatted(materia.getCodigo()));
        }
        materiaRepository.delete(materia);
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
