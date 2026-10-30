package py.edu.upe.hernandarias.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Apariencia de la interfaz web elegida por cada usuario.
 *
 * @param modo          {@code oscuro}, {@code claro} o {@code sistema} (sigue al sistema operativo)
 * @param colorPrimario color de botones y resaltados, en formato {@code #rrggbb}
 * @param colorBarra    color de la barra lateral, en formato {@code #rrggbb}
 * @param densidad      {@code comoda} o {@code compacta} (espaciado de tablas y formularios)
 * @param tamanoTexto   {@code normal} o {@code grande}
 */
public record PreferenciasDto(
        @NotNull @Pattern(regexp = "oscuro|claro|sistema") String modo,
        @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String colorPrimario,
        @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String colorBarra,
        @NotNull @Pattern(regexp = "comoda|compacta") String densidad,
        @NotNull @Pattern(regexp = "normal|grande") String tamanoTexto) {
}
