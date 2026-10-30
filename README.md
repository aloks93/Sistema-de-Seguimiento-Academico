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

La aplicación queda disponible en `http://localhost:8080`: al abrir esa dirección en el navegador se muestra
la **interfaz web** (login y pantallas por rol) y la API REST responde bajo `/api`.

### Atajos para Windows (doble clic)

| Archivo | Qué hace |
|---|---|
| `MENU.cmd` | Menú con todas las opciones de abajo |
| `iniciar-todo.cmd` | Verifica requisitos, levanta la base de datos, arranca el backend y abre el navegador |
| `comandos\verificar-requisitos.cmd` | Lista lo que le falta instalar a la computadora |
| `comandos\iniciar-base-de-datos.cmd` | Solo PostgreSQL (Docker) |
| `comandos\iniciar-backend.cmd` | Solo el backend (la base tiene que estar encendida) |
| `comandos\abrir-pagina-web.cmd` | Abre `http://localhost:8080` |
| `comandos\estado.cmd` | Muestra qué está encendido |
| `comandos\probar-api.cmd` | Prueba rápida de la API con los usuarios de ejemplo |
| `comandos\ejecutar-tests.cmd` | `mvnw test` |
| `comandos\detener-backend.cmd` / `detener-base-de-datos.cmd` / `detener-todo.cmd` | Apagan cada parte (los datos se conservan) |
| `comandos\borrar-base-de-datos.cmd` | Borra todos los datos (pide confirmación) |

Los `.cmd` llaman a los scripts de PowerShell de `scripts\`. Explicación detallada en `GUIA-DEL-PROYECTO.txt`.

### Interfaz web

| Rol | Puede |
|---|---|
| ADMIN (super usuario) | Gestionar carreras, docentes, materias (y qué docente está a cargo de cada una), estudiantes, administradores y todas las notas |
| DOCENTE | Ver, dar de alta y editar estudiantes **solo de las carreras donde tiene materias**; cargar, corregir y eliminar notas **solo de las materias que tiene a cargo** |
| ESTUDIANTE | Ver sus notas, el docente de cada materia y su promedio |

Un estudiante solo puede recibir notas de materias de su carrera. Las reglas se aplican en el servidor
(`PermisosService`): lo que no corresponde al docente no aparece en los listados y la API responde `403`.

Pantallas: panel de inicio con indicadores y avance de carga, **planilla de notas por materia** (carga
rápida con Enter y guardado en lote), gestión con búsqueda y filtros, y **Ajustes** donde cada usuario
elige modo oscuro/claro, color principal, color de la barra lateral, densidad y tamaño de texto (se guarda
en su cuenta) y cambia su contraseña. Por defecto: azul y negro.

Es HTML + CSS + JavaScript (módulos ES) sin dependencias, en `src/main/resources/static/`; no requiere Node
ni compilación.

El esquema de la base lo crean y actualizan las migraciones de Flyway al arrancar
(`src/main/resources/db/migration`); en el perfil `dev` además se cargan datos de ejemplo:

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `docente` | `docente123` | DOCENTE (a cargo de las materias de Informática) |
| `estudiante` | `estudiante123` | ESTUDIANTE (matrícula `2026-0001`, Informática) |

Carreras de ejemplo: `INF` Ingeniería en Informática y `ADM` Administración de Empresas.
Materias de ejemplo: `INF-101`, `INF-102`, `INF-201` (ids 1, 2, 3) y `ADM-101` sin docente asignado.

## Configuración

Todas las variables se leen del entorno (ver [`.env.example`](.env.example)):

| Variable | Default | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Usar otro perfil (p. ej. `prod`) en producción |
| `DB_URL` | `jdbc:postgresql://localhost:5432/seguimiento_academico` | URL JDBC |
| `DB_USERNAME` / `DB_PASSWORD` | `upe` / `upe` | Credenciales de la BD |
| `JWT_SECRET` | solo tiene default en `dev` | Clave HMAC en Base64, mín. 256 bits (`openssl rand -base64 32`) |
| `JWT_EXPIRATION` | `PT8H` | Duración del token (ISO-8601) |
| `JPA_DDL_AUTO` | `update` en dev, `validate` fuera de dev | Estrategia de Hibernate (el esquema lo gestiona Flyway) |

> ⚠️ Nunca subir secretos reales al repositorio. El archivo `.env` está en `.gitignore`.

## Endpoints

| Método | Ruta | Roles | Descripción |
|---|---|---|---|
| `POST` | `/api/auth/login` | público | Autentica y devuelve un JWT |
| `GET` | `/api/estudiantes/me/calificaciones` | ESTUDIANTE | Notas del estudiante autenticado |
| `GET` | `/api/calificaciones?estudianteId=&materiaId=` | DOCENTE, ADMIN | Lista notas (filtros opcionales; el docente solo ve las de sus materias) |
| `POST` | `/api/calificaciones` | DOCENTE\*, ADMIN | Registra una nota |
| `PATCH` | `/api/calificaciones/{id}` | DOCENTE\*, ADMIN | Modifica una nota existente |
| `DELETE` | `/api/calificaciones/{id}` | DOCENTE\*, ADMIN | Elimina una nota |
| `GET` | `/api/estudiantes?carreraId=` | DOCENTE\*\*, ADMIN | Lista estudiantes (filtro opcional por carrera) |
| `POST` | `/api/estudiantes` | DOCENTE\*\*, ADMIN | Crea un estudiante y su usuario (rol ESTUDIANTE) |
| `PUT` | `/api/estudiantes/{id}` | DOCENTE\*\*, ADMIN | Modifica datos, carrera y, opcionalmente, la contraseña |
| `DELETE` | `/api/estudiantes/{id}` | ADMIN | Elimina un estudiante sin notas (y su usuario) |
| `GET` | `/api/materias?carreraId=` | DOCENTE\*, ADMIN | Lista materias con su carrera y docente |
| `POST` | `/api/materias` | ADMIN | Crea una materia (carrera obligatoria, docente opcional) |
| `PUT` | `/api/materias/{id}` | ADMIN | Modifica una materia o cambia su docente |
| `DELETE` | `/api/materias/{id}` | ADMIN | Elimina una materia sin notas |
| `GET` | `/api/carreras` | DOCENTE\*\*, ADMIN | Lista carreras con su cantidad de materias y estudiantes |
| `POST` | `/api/carreras` | ADMIN | Crea una carrera |
| `PUT` | `/api/carreras/{id}` | ADMIN | Modifica una carrera |
| `DELETE` | `/api/carreras/{id}` | ADMIN | Elimina una carrera sin materias ni estudiantes |
| `GET` | `/api/docentes` | ADMIN | Lista docentes con sus materias a cargo |
| `POST` | `/api/docentes` | ADMIN | Crea un docente y su usuario (rol DOCENTE) |
| `PUT` | `/api/docentes/{id}` | ADMIN | Modifica el nombre y, opcionalmente, la contraseña |
| `DELETE` | `/api/docentes/{id}` | ADMIN | Elimina un docente sin materias a cargo (y su usuario) |
| `GET` | `/api/usuarios` | ADMIN | Lista todos los usuarios |
| `POST` | `/api/usuarios` | ADMIN | Crea un usuario ADMIN |
| `PATCH` | `/api/usuarios/{id}` | ADMIN | Activa/desactiva o cambia la contraseña |
| `GET` | `/api/perfil` | autenticado | Usuario, rol, nombre y preferencias de apariencia |
| `PUT` | `/api/perfil/preferencias` | autenticado | Guarda modo, colores (`#rrggbb`), densidad y tamaño de texto |
| `PUT` | `/api/perfil/contrasena` | autenticado | Cambia la propia contraseña (exige la actual) |

\* Un DOCENTE solo ve y opera sobre las materias que tiene a cargo (si no, `403`).
\*\* Un DOCENTE solo ve y gestiona las carreras (y sus estudiantes) donde tiene materias a cargo.

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
| `403` | El rol no tiene permiso, o un docente opera sobre una materia que no tiene a cargo |
| `404` | Recurso inexistente |
| `409` | Duplicado (nota para ese estudiante y materia, matrícula, código o usuario), materia de otra carrera que la del estudiante, o baja de un registro en uso |

## Modelo de datos

```
carreras (id, codigo, nombre)
   ├── 1:N ── estudiantes (id, usuario_id, matricula, nombre_completo, carrera_id)
   └── 1:N ── materias (id, codigo, nombre, creditos, semestre, carrera_id, docente_id)

usuarios (id, username, password_hash, rol, activo, preferencias)
   ├── 1:1 ── estudiantes.usuario_id
   └── 1:1 ── docentes (id, usuario_id, nombre_completo)
                 └── 1:N ── materias.docente_id   (docente a cargo, opcional)

calificaciones (id, estudiante_id, materia_id, nota, fecha_registro, fecha_actualizacion)
   UNIQUE (estudiante_id, materia_id) · CHECK (nota BETWEEN 0 AND 100)
```

Los cambios de esquema se hacen **siempre con una migración nueva** (`V4__descripcion.sql`, …) en
`src/main/resources/db/migration`; nunca se modifica una migración ya subida.

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

src/main/resources/static/        Interfaz web: index.html, css/estilos.css y js/ (app, nucleo, ui, tema)
src/main/resources/db/migration/  Migraciones de la base de datos (Flyway)
scripts/                     Scripts de Windows usados por los .cmd de la raíz
```

## Tests

```bash
./mvnw test
```

Los tests de integración usan una base H2 en memoria (perfil `test`), por lo que no necesitan Docker.

## Contribuir

Ver [CONTRIBUTING.md](CONTRIBUTING.md) para el flujo de ramas, convención de commits y proceso de Pull Requests.
