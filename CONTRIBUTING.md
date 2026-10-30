# Guía de contribución

¡Gracias por colaborar! Esta guía resume cómo trabajamos para mantener el repositorio ordenado.

## Flujo de trabajo

1. **Nunca se trabaja directamente sobre `main`.** `main` siempre debe compilar y pasar los tests.
2. Crear una rama a partir de `main` actualizado:

   ```bash
   git switch main
   git pull
   git switch -c feature/consulta-materias
   ```

   | Prefijo | Uso |
   |---|---|
   | `feature/` | Nueva funcionalidad |
   | `fix/` | Corrección de errores |
   | `docs/` | Solo documentación |
   | `refactor/` | Cambios internos sin alterar comportamiento |
   | `test/` | Agregar o mejorar tests |

3. Hacer commits pequeños y enfocados (ver convención abajo).
4. Antes de subir, verificar que todo pasa:

   ```bash
   ./mvnw verify
   ```

5. Subir la rama y abrir un **Pull Request** hacia `main` completando la plantilla.
6. Al menos **una revisión aprobada** antes de hacer merge. El CI debe estar en verde.

## Convención de commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/es/) con mensajes **en español**:

```
<tipo>: <descripción breve en imperativo o sustantivo>

[cuerpo opcional explicando el qué y el por qué]
```

Tipos: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`.

Ejemplos:

```
feat: endpoint para listar materias por semestre
fix: corregir validación de nota al actualizar calificación
docs: documentar variables de entorno en el README
```

## Estilo de código

- Respetar la arquitectura en capas: `controller → service → repository`. Los controladores no acceden a repositorios directamente.
- Las entidades JPA no se exponen en la API: usar DTOs (`record`) en el paquete `dto`.
- Validar la entrada con Bean Validation (`@Valid`, `@NotNull`, `@Min`, …).
- Errores de negocio: lanzar excepciones del paquete `exception`; el `GlobalExceptionHandler` las traduce a HTTP.
- Nuevos endpoints protegidos: declarar los roles en `SecurityConfig` y cubrirlos con tests.
- Cambios en las entidades que alteren tablas o columnas: agregar una migración nueva en
  `src/main/resources/db/migration` (`V5__descripcion.sql`, `V6__…`). **Nunca** modificar una migración ya
  subida a `main`: Flyway la detecta como alterada y la aplicación no arranca.
- Nombres de dominio en español (`Calificacion`, `registrar`, …), igual que el código existente.
- El formato básico (indentación, finales de línea) lo define `.editorconfig`; la mayoría de los IDEs lo aplican automáticamente.

## Tests

- Toda funcionalidad nueva o corrección debe incluir tests.
- Los tests de integración usan H2 con el perfil `test` (`src/test/resources/application-test.yml`).

## Seguridad

- **Nunca** subir contraseñas, claves JWT ni archivos `.env`.
- Si encontrás una vulnerabilidad, avisá en privado a las personas responsables del repositorio en lugar de abrir un issue público.

## Reportar problemas

Usá las plantillas de *Issues* (bug o mejora) e incluí pasos para reproducir, comportamiento esperado y el obtenido.
