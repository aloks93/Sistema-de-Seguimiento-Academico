-- Cuánto vale cada componente de la nota, por materia (lo define el docente a cargo o un ADMIN).
-- Las materias existentes quedan con el reglamento de la UPE: 15 + 15 + 20 + 50 = 100.
ALTER TABLE materias ADD COLUMN puntaje_primer_parcial   INTEGER NOT NULL DEFAULT 15;
ALTER TABLE materias ADD COLUMN puntaje_segundo_parcial  INTEGER NOT NULL DEFAULT 15;
ALTER TABLE materias ADD COLUMN puntaje_trabajo_practico INTEGER NOT NULL DEFAULT 20;
ALTER TABLE materias ADD COLUMN puntaje_examen_final     INTEGER NOT NULL DEFAULT 50;
ALTER TABLE materias ADD CONSTRAINT ck_materia_puntajes CHECK (
    puntaje_primer_parcial BETWEEN 0 AND 100
    AND puntaje_segundo_parcial BETWEEN 0 AND 100
    AND puntaje_trabajo_practico BETWEEN 0 AND 100
    AND puntaje_examen_final BETWEEN 1 AND 100
    AND puntaje_primer_parcial + puntaje_segundo_parcial + puntaje_trabajo_practico + puntaje_examen_final = 100);

-- Los componentes ya no tienen un máximo fijo (15/15/20/50): lo controla la aplicación según la materia
ALTER TABLE calificaciones DROP CONSTRAINT IF EXISTS calificaciones_primer_parcial_check;
ALTER TABLE calificaciones DROP CONSTRAINT IF EXISTS calificaciones_segundo_parcial_check;
ALTER TABLE calificaciones DROP CONSTRAINT IF EXISTS calificaciones_trabajo_practico_check;
ALTER TABLE calificaciones DROP CONSTRAINT IF EXISTS calificaciones_examen_final_check;
ALTER TABLE calificaciones ADD CONSTRAINT ck_calificacion_componentes_rango CHECK (
    (primer_parcial IS NULL OR primer_parcial BETWEEN 0 AND 100)
    AND (segundo_parcial IS NULL OR segundo_parcial BETWEEN 0 AND 100)
    AND (trabajo_practico IS NULL OR trabajo_practico BETWEEN 0 AND 100)
    AND (examen_final IS NULL OR examen_final BETWEEN 0 AND 100));
