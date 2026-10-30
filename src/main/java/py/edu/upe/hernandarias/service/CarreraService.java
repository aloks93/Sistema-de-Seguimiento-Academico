package py.edu.upe.hernandarias.service;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import py.edu.upe.hernandarias.dto.CarreraRequest;
import py.edu.upe.hernandarias.dto.CarreraResponse;
import py.edu.upe.hernandarias.entity.Carrera;
import py.edu.upe.hernandarias.exception.ConflictoException;
import py.edu.upe.hernandarias.exception.RecursoNoEncontradoException;
import py.edu.upe.hernandarias.repository.CarreraRepository;
import py.edu.upe.hernandarias.repository.EstudianteRepository;
import py.edu.upe.hernandarias.repository.MateriaRepository;

@Service
@RequiredArgsConstructor
public class CarreraService {

    private final CarreraRepository carreraRepository;
    private final MateriaRepository materiaRepository;
    private final EstudianteRepository estudianteRepository;
    private final PermisosService permisosService;

    /** Todas las carreras para un ADMIN; para un docente, solo aquellas donde tiene materias. */
    @Transactional(readOnly = true)
    public List<CarreraResponse> listar(String username) {
        List<Carrera> carreras = carreraRepository.findAllByOrderByNombreAsc();
        if (!permisosService.esAdmin(username)) {
            Set<Long> propias = permisosService.carrerasDelDocente(username);
            carreras = carreras.stream().filter(carrera -> propias.contains(carrera.getId())).toList();
        }
        return carreras.stream().map(this::respuesta).toList();
    }

    @Transactional
    public CarreraResponse crear(CarreraRequest request) {
        String codigo = request.codigo().trim();
        String nombre = request.nombre().trim();
        if (carreraRepository.existsByCodigo(codigo)) {
            throw new ConflictoException("Ya existe una carrera con el código " + codigo);
        }
        if (carreraRepository.existsByNombre(nombre)) {
            throw new ConflictoException("Ya existe la carrera " + nombre);
        }

        Carrera carrera = carreraRepository.save(Carrera.builder().codigo(codigo).nombre(nombre).build());
        return respuesta(carrera);
    }

    @Transactional
    public CarreraResponse actualizar(Long id, CarreraRequest request) {
        Carrera carrera = buscar(id);
        String codigo = request.codigo().trim();
        String nombre = request.nombre().trim();
        if (carreraRepository.existsByCodigoAndIdNot(codigo, id)) {
            throw new ConflictoException("Ya existe otra carrera con el código " + codigo);
        }
        if (carreraRepository.existsByNombreAndIdNot(nombre, id)) {
            throw new ConflictoException("Ya existe otra carrera llamada " + nombre);
        }

        carrera.setCodigo(codigo);
        carrera.setNombre(nombre);
        return respuesta(carreraRepository.save(carrera));
    }

    /** Solo se eliminan carreras vacías (sin materias ni estudiantes). */
    @Transactional
    public void eliminar(Long id) {
        Carrera carrera = buscar(id);
        if (materiaRepository.countByCarreraId(id) > 0 || estudianteRepository.countByCarreraId(id) > 0) {
            throw new ConflictoException(
                    "La carrera %s tiene materias o estudiantes; muévalos o elimínelos primero"
                            .formatted(carrera.getNombre()));
        }
        carreraRepository.delete(carrera);
    }

    private CarreraResponse respuesta(Carrera carrera) {
        return CarreraResponse.from(carrera,
                materiaRepository.countByCarreraId(carrera.getId()),
                estudianteRepository.countByCarreraId(carrera.getId()));
    }

    private Carrera buscar(Long id) {
        return carreraRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Carrera", id));
    }
}
