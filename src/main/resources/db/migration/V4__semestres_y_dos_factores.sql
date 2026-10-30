-- Duración de cada carrera en semestres (las existentes quedan con 10; el administrador puede cambiarla)
ALTER TABLE carreras ADD COLUMN semestres INTEGER NOT NULL DEFAULT 10 CHECK (semestres BETWEEN 1 AND 12);

-- Verificación en dos pasos (TOTP / Google Authenticator):
-- totp_secreto = activa; totp_secreto_pendiente = generada pero todavía sin confirmar con un código
ALTER TABLE usuarios ADD COLUMN totp_secreto VARCHAR(64);
ALTER TABLE usuarios ADD COLUMN totp_secreto_pendiente VARCHAR(64);
