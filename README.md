# Sistema de Seguimiento Académico — UPE Filial Hernandarias

API REST para la gestión y consulta de calificaciones de estudiantes.

**Stack:** Java 21 · Spring Boot 3.5 · Spring Security + JWT · Spring Data JPA · PostgreSQL · Lombok · Maven

---

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | 21 | p. ej. [Eclipse Temurin](https://adoptium.net/) |
| Docker | cualquiera reciente | solo para levantar PostgreSQL en local |
| Maven | — | **no hace falta instalarlo**: se usa el wrapper `./mvnw` |

## Puesta en marcha

```bash
# 1. Clonar
git clone <url-del-repo>
cd seguimiento-academico

# 2. Levantar PostgreSQL (puerto 5432, usuario/clave: upe/upe)
docker compose up -d

# 3. Ejecutar la aplicación (perfil dev por defecto)
./mvnw spring-boot:run          # Linux / macOS / Git Bash
mvnw.cmd spring-boot:run        # Windows (cmd / PowerShell)
```

La API queda disponible en `http://localhost:8080`.

En el perfil `dev` el esquema se crea automáticamente y se cargan datos de ejemplo:

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `docente` | `docente123` | DOCENTE |
| `estudiante` | `estudiante123` | ESTUDIANTE (matrícula `2026-0001`) |

Materias de ejemplo: `INF-101`, `INF-102`, `INF-201` (ids 1, 2, 3).

## Configuración

Todas las variables se leen del entorno (ver [`.env.example`](.env.example)):

| Variable | Default | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Usar otro perfil (p. ej. `prod`) en producción |
| `DB_URL` | `jdbc:postgresql://localhost:5432/seguimiento_academico` | URL JDBC |
| `DB_USERNAME` / `DB_PASSWORD` | `upe` / `upe` | Credenciales de la BD |
| `JWT_SECRET` | solo tiene default en `dev` | Clave HMAC en Base64, mín. 256 bits (`openssl rand -base64 32`) |
| `JWT_EXPIRATION` | `PT8H` | Duración del token (ISO-8601) |
| `JPA_DDL_AUTO` | `update` en dev, `validate` fuera de dev | Estrategia de esquema de Hibernate |

> ⚠️ Nunca subir secretos reales al repositorio. El archivo `.env` está en `.gitignore`.

## Endpoints

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `POST` | `/api/auth/login` | público | Autentica y devuelve un JWT |
| `GET` | `/api/estudiantes/me/calificaciones` | ESTUDIANTE | Notas del estudiante autenticado |
| `POST` | `/api/calificaciones` | DOCENTE, ADMIN | Registra una nota |
| `PATCH` | `/api/calificaciones/{id}` | DOCENTE, ADMIN | Modifica una nota existente |

Las rutas protegidas requieren el header `Authorization: Bearer <token>`.
Los errores se devuelven en formato [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807) (`application/problem+json`).

### Ejemplos

```bash
# Login
curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"docente","password":"docente123"}'
# → {"token":"eyJ...","tipo":"Bearer","expiraEn":28800,"username":"docente","rol":"DOCENTE"}

# Registrar nota (docente)
curl -s -X POST localhost:8080/api/calificaciones \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"estudianteId":1,"materiaId":1,"nota":85}'

# Modificar nota
curl -s -X PATCH localhost:8080/api/calificaciones/1 \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"nota":90}'

# Consultar notas propias (con token de estudiante)
curl -s localhost:8080/api/estudiantes/me/calificaciones -H "Authorization: Bearer $TOKEN_ESTUDIANTE"
```

| Código | Cuándo |
|---|---|
| `400` | Validación fallida (p. ej. nota fuera de 0–100) |
| `401` | Sin token, token inválido/expirado o credenciales incorrectas |
| `403` | El rol no tiene permiso para la operación |
| `404` | Estudiante, materia o calificación inexistente |
| `409` | Ya existe una nota para ese estudiante y materia |

## Modelo de datos

```
usuarios (id, username, password_hash, rol, activo)
   │ 1:1
estudiantes (id, usuario_id, matricula, nombre_completo, carrera)
   │ 1:N
calificaciones (id, estudiante_id, materia_id, nota, fecha_registro, fecha_actualizacion)
   │            UNIQUE (estudiante_id, materia_id) · CHECK (nota BETWEEN 0 AND 100)
   │ N:1
materias (id, codigo, nombre, creditos, semestre)
```

## Estructura del proyecto

```
src/main/java/py/edu/upe/hernandarias/
├── controller/   Controladores REST
├── service/      Lógica de negocio
├── repository/   Acceso a datos (Spring Data JPA)
├── entity/       Entidades JPA
├── dto/          Objetos de entrada/salida de la API
├── security/     JWT, filtro de autenticación y configuración RBAC
├── exception/    Excepciones y manejador global de errores
└── config/       Configuración adicional (datos de ejemplo)
```

## Tests

```bash
./mvnw test
```

Los tests de integración usan una base H2 en memoria (perfil `test`), por lo que no necesitan Docker.

## Contribuir

Ver [CONTRIBUTING.md](CONTRIBUTING.md) para el flujo de ramas, convención de commits y proceso de Pull Requests.
