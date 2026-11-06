package py.edu.upe.hernandarias.entity;

/** Motivo de un pedido enviado desde la pantalla de inicio de sesión. */
public enum TipoSolicitud {
    /** Olvidó su contraseña y pide que un administrador la restablezca. */
    CONTRASENA,
    /** No tiene usuario y pide que le creen uno. */
    ACCESO
}
