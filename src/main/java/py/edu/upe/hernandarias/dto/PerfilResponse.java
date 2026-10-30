package py.edu.upe.hernandarias.dto;

import py.edu.upe.hernandarias.entity.Rol;

/**
 * @param nombre       nombre completo del docente o estudiante; el usuario para un ADMIN
 * @param preferencias apariencia guardada, o {@code null} si usa la de por defecto
 */
public record PerfilResponse(String username, Rol rol, String nombre, PreferenciasDto preferencias) {
}
