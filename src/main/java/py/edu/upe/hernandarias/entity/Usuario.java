package py.edu.upe.hernandarias.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Hash BCrypt de la contraseña; nunca se almacena la contraseña en texto plano. */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    /**
     * Único administrador que puede eliminar, desactivar o cambiar la contraseña de otros
     * administradores. Nadie puede eliminarlo ni desactivarlo.
     */
    @Builder.Default
    @Column(name = "super_admin", nullable = false)
    private boolean superAdmin = false;

    /** Preferencias de apariencia en JSON (ver PreferenciasDto); {@code null} = valores por defecto. */
    @Column(length = 1000)
    private String preferencias;

    /** Clave TOTP (Base32) de la verificación en dos pasos; {@code null} = desactivada. */
    @Column(name = "totp_secreto", length = 64)
    private String totpSecreto;

    /** Clave generada al empezar a activar el 2FA, hasta que se confirma con un código. */
    @Column(name = "totp_secreto_pendiente", length = 64)
    private String totpSecretoPendiente;

    public boolean tieneDosFactores() {
        return totpSecreto != null;
    }
}
