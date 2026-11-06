package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotNull;
import py.edu.upe.hernandarias.entity.EstadoSolicitud;

public record EstadoSolicitudRequest(@NotNull EstadoSolicitud estado) {
}
