-- Preferencias de apariencia de cada usuario (JSON validado por la aplicación; NULL = valores por defecto)
ALTER TABLE usuarios ADD COLUMN preferencias VARCHAR(1000);
