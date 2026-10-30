'use strict';

/*
 * Interfaz web del Sistema de Seguimiento Académico.
 * Consume la API REST del mismo servidor. El token JWT se guarda en sessionStorage,
 * así que la sesión se cierra al cerrar la pestaña del navegador.
 *
 * Roles:
 *   ADMIN      carreras, materias (con su docente), docentes, estudiantes, usuarios y todas las notas
 *   DOCENTE    alta y edición de estudiantes; notas solo de las materias que tiene a cargo
 *   ESTUDIANTE sus propias notas
 */

const estado = {
    sesion: null, // { token, username, rol }
    carreras: [],
    materias: [],
    estudiantes: [],
    docentes: [],
    notas: [],
};

const PESTANAS = {
    ADMIN: [
        ['notas', 'Notas'], ['estudiantes', 'Estudiantes'], ['materias', 'Materias'],
        ['docentes', 'Docentes'], ['carreras', 'Carreras'], ['usuarios', 'Usuarios'],
    ],
    DOCENTE: [['notas', 'Notas'], ['estudiantes', 'Estudiantes'], ['materias', 'Mis materias']],
    ESTUDIANTE: [['misNotas', 'Mis notas']],
};

const NOMBRES_ROL = { ADMIN: 'Administrador', DOCENTE: 'Docente', ESTUDIANTE: 'Estudiante' };

const NOMBRES_CAMPO = {
    nota: 'Nota', codigo: 'Código', nombre: 'Nombre', creditos: 'Créditos', semestre: 'Semestre',
    matricula: 'Matrícula', nombreCompleto: 'Nombre completo', carreraId: 'Carrera', docenteId: 'Docente',
    username: 'Usuario', password: 'Contraseña',
};

const $ = (selector) => document.querySelector(selector);
const esAdmin = () => estado.sesion?.rol === 'ADMIN';

// ======================= Utilidades =======================

/** Crea un elemento del DOM. Los textos se insertan como texto, nunca como HTML. */
function el(etiqueta, atributos = {}, ...hijos) {
    const nodo = document.createElement(etiqueta);
    for (const [clave, valor] of Object.entries(atributos)) {
        if (clave === 'class') nodo.className = valor;
        else if (clave.startsWith('on')) nodo.addEventListener(clave.slice(2), valor);
        else nodo.setAttribute(clave, valor);
    }
    nodo.append(...hijos.filter((hijo) => hijo !== null && hijo !== undefined));
    return nodo;
}

function botonChico(texto, accion, peligro = false) {
    const clase = 'boton boton--chico' + (peligro ? ' boton--peligro' : '');
    return el('button', { type: 'button', class: clase, onclick: accion }, texto);
}

function celdaEstado(activo) {
    return el('td', { class: activo ? 'estado--activo' : 'estado--inactivo' }, activo ? 'Activo' : 'Inactivo');
}

function dibujarFilas(selectorTabla, filas, columnas, textoVacio) {
    const cuerpo = document.querySelector(selectorTabla + ' tbody');
    const vacio = el('tr', {}, el('td', { class: 'vacio', colspan: columnas }, textoVacio));
    cuerpo.replaceChildren(...(filas.length ? filas : [vacio]));
}

function llenarSelect(select, elementos, texto, opcionVacia) {
    const anterior = select.value;
    select.replaceChildren(
        el('option', { value: '' }, opcionVacia),
        ...elementos.map((elemento) => el('option', { value: elemento.id }, texto(elemento))));
    if (elementos.some((elemento) => String(elemento.id) === anterior)) select.value = anterior;
}

const textoCarrera = (c) => `${c.nombre} (${c.codigo})`;
const textoEstudiante = (e) => `${e.nombreCompleto} (${e.matricula})`;
const textoMateria = (m) => `${m.codigo} · ${m.nombre} — ${m.carreraCodigo}`;
const conFiltro = (ruta, parametro, valor) => (valor ? `${ruta}?${parametro}=${encodeURIComponent(valor)}` : ruta);

function formatearFecha(iso) {
    return iso ? new Date(iso).toLocaleDateString('es-PY', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '';
}

let temporizadorAviso;
function avisar(texto, tipo = 'info') {
    const aviso = $('#aviso');
    aviso.textContent = texto;
    aviso.className = 'aviso' + (tipo === 'info' ? '' : ' aviso--' + tipo);
    aviso.hidden = false;
    clearTimeout(temporizadorAviso);
    temporizadorAviso = setTimeout(() => { aviso.hidden = true; }, tipo === 'error' ? 7000 : 3500);
}

// ======================= Sesión =======================

function leerSesion() {
    try {
        return JSON.parse(sessionStorage.getItem('sesion'));
    } catch {
        return null;
    }
}

function guardarSesion(sesion) {
    try {
        if (sesion) sessionStorage.setItem('sesion', JSON.stringify(sesion));
        else sessionStorage.removeItem('sesion');
    } catch {
        // Sin almacenamiento disponible: la sesión dura hasta recargar la página
    }
}

async function ingresar(evento) {
    evento.preventDefault();
    const form = evento.currentTarget;
    const boton = form.querySelector('[type=submit]');
    boton.disabled = true;
    try {
        const { username, password } = Object.fromEntries(new FormData(form));
        const respuesta = await api('POST', '/api/auth/login', { username, password });
        estado.sesion = { token: respuesta.token, username: respuesta.username, rol: respuesta.rol };
        guardarSesion(estado.sesion);
        form.reset();
        mostrarApp();
    } catch (error) {
        avisar(error.message, 'error');
    } finally {
        boton.disabled = false;
    }
}

function cerrarSesion(mensaje) {
    estado.sesion = null;
    guardarSesion(null);
    $('#vista-app').hidden = true;
    $('#vista-login').hidden = false;
    if (mensaje) avisar(mensaje);
}

// ======================= API =======================

class ErrorApi extends Error {
    constructor(status, problema) {
        super(mensajeDeError(status, problema));
        this.status = status;
    }
}

/** Arma un mensaje legible a partir de la respuesta de error (RFC 7807) del servidor. */
function mensajeDeError(status, problema) {
    if (status === 403 && !problema?.detail) return 'No tiene permiso para esta operación.';
    if (!problema) return `Error ${status} del servidor.`;
    let mensaje = problema.detail || problema.title || `Error ${status}`;
    if (problema.errores) {
        const detalles = Object.entries(problema.errores)
            .map(([campo, error]) => `${NOMBRES_CAMPO[campo] || campo}: ${error}`);
        mensaje += ' — ' + detalles.join('; ');
    }
    return mensaje;
}

async function api(metodo, ruta, cuerpo) {
    const opciones = { method: metodo, headers: {} };
    if (estado.sesion) opciones.headers.Authorization = 'Bearer ' + estado.sesion.token;
    if (cuerpo !== undefined) {
        opciones.headers['Content-Type'] = 'application/json';
        opciones.body = JSON.stringify(cuerpo);
    }

    let respuesta;
    try {
        respuesta = await fetch(ruta, opciones);
    } catch {
        throw new Error('No se pudo conectar con el servidor. ¿Sigue abierta la ventana del backend (iniciar-todo.cmd)?');
    }

    if (respuesta.status === 401 && estado.sesion) {
        cerrarSesion('La sesión expiró. Ingrese de nuevo.');
        throw new Error('La sesión expiró.');
    }
    const esJson = (respuesta.headers.get('Content-Type') || '').includes('json');
    const datos = esJson ? await respuesta.json() : null;
    if (!respuesta.ok) throw new ErrorApi(respuesta.status, datos);
    return datos;
}

async function eliminar(ruta, descripcion, recargar) {
    if (!confirm(`¿Eliminar ${descripcion}? Esta acción no se puede deshacer.`)) return;
    try {
        await api('DELETE', ruta);
        avisar('Eliminado.', 'exito');
        await recargar();
    } catch (error) {
        avisar(error.message, 'error');
    }
}

// ======================= Navegación =======================

const CARGADORES = {
    notas: cargarPestanaNotas,
    estudiantes: cargarPestanaEstudiantes,
    materias: cargarPestanaMaterias,
    docentes: cargarDocentes,
    carreras: cargarCarreras,
    usuarios: cargarUsuarios,
    misNotas: cargarMisNotas,
};

function mostrarApp() {
    const { username, rol } = estado.sesion;
    $('#vista-login').hidden = true;
    $('#vista-app').hidden = false;
    $('#usuario-actual').replaceChildren(username + ' ', el('span', { class: 'etiqueta-rol' }, NOMBRES_ROL[rol] || rol));
    document.querySelectorAll('[data-solo-admin]').forEach((nodo) => { nodo.hidden = rol !== 'ADMIN'; });

    const pestanas = PESTANAS[rol] || [];
    $('#pestanas').replaceChildren(...pestanas.map(([id, texto]) =>
        el('button', { type: 'button', 'data-pestana': id, onclick: () => abrirPestana(id) }, texto)));
    if (pestanas.length) abrirPestana(pestanas[0][0]);
}

async function abrirPestana(id) {
    document.querySelectorAll('.seccion').forEach((seccion) => { seccion.hidden = seccion.id !== 'seccion-' + id; });
    document.querySelectorAll('#pestanas button').forEach((boton) => {
        if (boton.dataset.pestana === id) boton.setAttribute('aria-current', 'page');
        else boton.removeAttribute('aria-current');
    });
    try {
        await CARGADORES[id]();
    } catch (error) {
        avisar(error.message, 'error');
    }
}

// ======================= Formularios (alta y edición) =======================

/** Conecta un formulario: al enviarlo llama a alGuardar(datos); si no devuelve false, lo limpia. */
function configurarFormulario(form, alGuardar) {
    const boton = form.querySelector('[type=submit]');
    form.dataset.textoCrear = boton.textContent;
    form.dataset.tituloCrear = form.querySelector('.formulario__titulo').textContent;

    form.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        boton.disabled = true;
        try {
            if (await alGuardar(Object.fromEntries(new FormData(form))) !== false) salirDeEdicion(form);
        } catch (error) {
            avisar(error.message, 'error');
        } finally {
            boton.disabled = false;
        }
    });
    form.querySelector('[data-cancelar]')?.addEventListener('click', () => salirDeEdicion(form));
}

function entrarEnEdicion(form, valores, titulo) {
    for (const [campo, valor] of Object.entries(valores)) {
        if (form.elements[campo]) form.elements[campo].value = valor ?? '';
    }
    form.querySelectorAll('[data-bloquear-al-editar]').forEach((campo) => { campo.disabled = true; });
    form.querySelectorAll('[data-opcional-al-editar]').forEach((campo) => { campo.required = false; campo.value = ''; });
    form.querySelectorAll('[data-ayuda-editar]').forEach((nodo) => { nodo.hidden = false; });
    form.querySelector('[data-cancelar]').hidden = false;
    form.querySelector('[type=submit]').textContent = 'Guardar cambios';
    form.querySelector('.formulario__titulo').textContent = titulo;
    form.classList.add('editando');
    form.scrollIntoView({ behavior: 'smooth', block: 'start' });
    form.querySelector('input:not([type=hidden]):not(:disabled), select:not(:disabled)')?.focus();
}

function salirDeEdicion(form) {
    form.reset();
    if (form.elements.id) form.elements.id.value = '';
    form.querySelectorAll('[data-bloquear-al-editar]').forEach((campo) => { campo.disabled = false; });
    form.querySelectorAll('[data-opcional-al-editar]').forEach((campo) => { campo.required = true; });
    form.querySelectorAll('[data-ayuda-editar]').forEach((nodo) => { nodo.hidden = true; });
    const cancelar = form.querySelector('[data-cancelar]');
    if (cancelar) cancelar.hidden = true;
    form.querySelector('[type=submit]').textContent = form.dataset.textoCrear;
    form.querySelector('.formulario__titulo').textContent = form.dataset.tituloCrear;
    form.classList.remove('editando');
}

// ======================= Notas =======================

/** Materias en las que el usuario puede cargar notas: todas (ADMIN) o las que tiene a cargo (DOCENTE). */
function misMaterias() {
    return esAdmin()
        ? estado.materias
        : estado.materias.filter((materia) => materia.docenteUsername === estado.sesion.username);
}

async function cargarPestanaNotas() {
    [estado.materias, estado.estudiantes] = await Promise.all([
        api('GET', '/api/materias'),
        api('GET', '/api/estudiantes'),
    ]);
    const materias = misMaterias();
    const sinMaterias = materias.length === 0;
    $('#pista-sin-materias').hidden = !sinMaterias;
    $('#form-nota').hidden = sinMaterias;

    llenarSelect($('#form-nota').elements.materiaId, materias, textoMateria, 'Elegir materia…');
    actualizarEstudiantesDeNota();
    llenarSelect($('#filtro-nota-materia'), materias, textoMateria,
        esAdmin() ? 'Todas las materias' : 'Todas mis materias');
    llenarSelect($('#filtro-nota-estudiante'), estado.estudiantes, textoEstudiante, 'Todos los estudiantes');
    await cargarNotas();
}

/** El estudiante debe ser de la carrera de la materia elegida. */
function actualizarEstudiantesDeNota() {
    const form = $('#form-nota');
    const materia = estado.materias.find((m) => String(m.id) === form.elements.materiaId.value);
    const estudiantes = materia ? estado.estudiantes.filter((e) => e.carreraId === materia.carreraId) : [];
    let opcionVacia = 'Primero elija la materia';
    if (materia) opcionVacia = estudiantes.length ? 'Elegir estudiante…' : 'No hay estudiantes en esa carrera';
    llenarSelect(form.elements.estudianteId, estudiantes, textoEstudiante, opcionVacia);
}

async function cargarNotas() {
    const parametros = new URLSearchParams();
    if ($('#filtro-nota-materia').value) parametros.set('materiaId', $('#filtro-nota-materia').value);
    if ($('#filtro-nota-estudiante').value) parametros.set('estudianteId', $('#filtro-nota-estudiante').value);
    const consulta = parametros.toString();
    estado.notas = await api('GET', '/api/calificaciones' + (consulta ? '?' + consulta : ''));

    const filas = estado.notas.map((nota) => el('tr', {},
        el('td', {}, nota.nombreEstudiante),
        el('td', {}, nota.matricula),
        el('td', {}, `${nota.codigoMateria} · ${nota.nombreMateria}`),
        el('td', {}, nota.semestre),
        el('td', { class: 'num nota' }, nota.nota),
        el('td', {}, formatearFecha(nota.fechaActualizacion)),
        el('td', { class: 'acciones' },
            botonChico('Editar', () => editarNota(nota)),
            botonChico('Eliminar', () => eliminar(`/api/calificaciones/${nota.id}`,
                `la nota de ${nota.nombreEstudiante} en ${nota.codigoMateria}`, cargarNotas), true))));
    dibujarFilas('#tabla-notas', filas, 7, consulta ? 'No hay notas con ese filtro.' : 'Todavía no hay notas cargadas.');
}

function editarNota(nota) {
    const form = $('#form-nota');
    form.elements.materiaId.value = nota.materiaId;
    actualizarEstudiantesDeNota();
    entrarEnEdicion(form,
        { id: nota.id, materiaId: nota.materiaId, estudianteId: nota.estudianteId, nota: nota.nota },
        `Editar nota de ${nota.nombreEstudiante} en ${nota.codigoMateria}`);
}

async function guardarNota(datos) {
    const nota = Number(datos.nota);
    if (datos.id) {
        await api('PATCH', `/api/calificaciones/${datos.id}`, { nota });
        avisar('Nota actualizada.', 'exito');
    } else {
        const estudianteId = Number(datos.estudianteId);
        const materiaId = Number(datos.materiaId);
        // Si ya tiene nota en esa materia se ofrece reemplazarla en lugar de mostrar un error
        const [existente] = await api('GET', `/api/calificaciones?estudianteId=${estudianteId}&materiaId=${materiaId}`);
        if (existente) {
            if (!confirm(`Ese estudiante ya tiene ${existente.nota} en ${existente.codigoMateria}. ¿Reemplazarla por ${nota}?`)) {
                return false;
            }
            await api('PATCH', `/api/calificaciones/${existente.id}`, { nota });
            avisar('Nota actualizada.', 'exito');
        } else {
            await api('POST', '/api/calificaciones', { estudianteId, materiaId, nota });
            avisar('Nota guardada.', 'exito');
        }
    }
    await cargarNotas();
}

// ======================= Estudiantes =======================

async function cargarPestanaEstudiantes() {
    estado.carreras = await api('GET', '/api/carreras');
    llenarSelect($('#form-estudiante').elements.carreraId, estado.carreras, textoCarrera, 'Elegir carrera…');
    llenarSelect($('#filtro-estudiante-carrera'), estado.carreras, textoCarrera, 'Todas las carreras');
    await cargarEstudiantes();
}

async function cargarEstudiantes() {
    estado.estudiantes = await api('GET',
        conFiltro('/api/estudiantes', 'carreraId', $('#filtro-estudiante-carrera').value));
    const filas = estado.estudiantes.map((estudiante) => el('tr', {},
        el('td', {}, estudiante.nombreCompleto),
        el('td', {}, estudiante.matricula),
        el('td', {}, estudiante.carreraNombre),
        el('td', {}, estudiante.username),
        celdaEstado(estudiante.activo),
        el('td', { class: 'acciones' },
            botonChico('Editar', () => editarEstudiante(estudiante)),
            esAdmin() ? botonChico('Eliminar', () => eliminar(`/api/estudiantes/${estudiante.id}`,
                `al estudiante ${estudiante.nombreCompleto}`, cargarEstudiantes), true) : null)));
    dibujarFilas('#tabla-estudiantes', filas, 6, 'No hay estudiantes cargados.');
}

function editarEstudiante(estudiante) {
    entrarEnEdicion($('#form-estudiante'), {
        id: estudiante.id,
        nombreCompleto: estudiante.nombreCompleto,
        matricula: estudiante.matricula,
        carreraId: estudiante.carreraId,
        username: estudiante.username,
    }, `Editar a ${estudiante.nombreCompleto}`);
}

async function guardarEstudiante(datos) {
    if (datos.id) {
        await api('PUT', `/api/estudiantes/${datos.id}`, {
            matricula: datos.matricula,
            nombreCompleto: datos.nombreCompleto,
            carreraId: Number(datos.carreraId),
            password: datos.password || null,
        });
        avisar('Estudiante actualizado.', 'exito');
    } else {
        await api('POST', '/api/estudiantes', {
            username: datos.username,
            password: datos.password,
            matricula: datos.matricula,
            nombreCompleto: datos.nombreCompleto,
            carreraId: Number(datos.carreraId),
        });
        avisar(`Estudiante creado. Ya puede ingresar con el usuario "${datos.username.trim()}".`, 'exito');
    }
    await cargarEstudiantes();
}

// ======================= Materias =======================

async function cargarPestanaMaterias() {
    $('#titulo-materias').textContent = esAdmin() ? 'Materias' : 'Mis materias';
    if (esAdmin()) {
        [estado.carreras, estado.docentes] = await Promise.all([
            api('GET', '/api/carreras'),
            api('GET', '/api/docentes'),
        ]);
        const form = $('#form-materia');
        llenarSelect(form.elements.carreraId, estado.carreras, textoCarrera, 'Elegir carrera…');
        llenarSelect(form.elements.docenteId, estado.docentes, (d) => `${d.nombreCompleto} (${d.username})`, 'Sin asignar');
        llenarSelect($('#filtro-materia-carrera'), estado.carreras, textoCarrera, 'Todas las carreras');
    }
    await cargarMaterias();
}

async function cargarMaterias() {
    const carreraId = esAdmin() ? $('#filtro-materia-carrera').value : '';
    estado.materias = await api('GET', conFiltro('/api/materias', 'carreraId', carreraId));
    const filas = misMaterias().map((materia) => el('tr', {},
        el('td', {}, materia.codigo),
        el('td', {}, materia.nombre),
        el('td', {}, materia.carreraNombre),
        el('td', { class: 'num' }, materia.semestre),
        el('td', { class: 'num' }, materia.creditos),
        materia.docenteNombre ? el('td', {}, materia.docenteNombre) : el('td', { class: 'sutil' }, 'Sin asignar'),
        esAdmin() ? el('td', { class: 'acciones' },
            botonChico('Editar', () => editarMateria(materia)),
            botonChico('Eliminar', () => eliminar(`/api/materias/${materia.id}`,
                `la materia ${materia.codigo}`, cargarMaterias), true)) : null));
    dibujarFilas('#tabla-materias', filas, esAdmin() ? 7 : 6,
        esAdmin() ? 'No hay materias cargadas.' : 'Todavía no tiene materias a cargo.');
}

function editarMateria(materia) {
    entrarEnEdicion($('#form-materia'), {
        id: materia.id,
        codigo: materia.codigo,
        nombre: materia.nombre,
        carreraId: materia.carreraId,
        semestre: materia.semestre,
        creditos: materia.creditos,
        docenteId: materia.docenteId,
    }, `Editar ${materia.codigo}`);
}

async function guardarMateria(datos) {
    const cuerpo = {
        codigo: datos.codigo,
        nombre: datos.nombre,
        creditos: Number(datos.creditos),
        semestre: Number(datos.semestre),
        carreraId: Number(datos.carreraId),
        docenteId: datos.docenteId ? Number(datos.docenteId) : null,
    };
    if (datos.id) {
        await api('PUT', `/api/materias/${datos.id}`, cuerpo);
        avisar('Materia actualizada.', 'exito');
    } else {
        await api('POST', '/api/materias', cuerpo);
        avisar('Materia creada.', 'exito');
    }
    await cargarMaterias();
}

// ======================= Docentes =======================

async function cargarDocentes() {
    estado.docentes = await api('GET', '/api/docentes');
    const filas = estado.docentes.map((docente) => el('tr', {},
        el('td', {}, docente.nombreCompleto),
        el('td', {}, docente.username),
        docente.materias.length
            ? el('td', {}, docente.materias.join(', '))
            : el('td', { class: 'sutil' }, 'Ninguna'),
        celdaEstado(docente.activo),
        el('td', { class: 'acciones' },
            botonChico('Editar', () => editarDocente(docente)),
            botonChico('Eliminar', () => eliminar(`/api/docentes/${docente.id}`,
                `al docente ${docente.nombreCompleto}`, cargarDocentes), true))));
    dibujarFilas('#tabla-docentes', filas, 5, 'No hay docentes cargados.');
}

function editarDocente(docente) {
    entrarEnEdicion($('#form-docente'),
        { id: docente.id, nombreCompleto: docente.nombreCompleto, username: docente.username },
        `Editar a ${docente.nombreCompleto}`);
}

async function guardarDocente(datos) {
    if (datos.id) {
        await api('PUT', `/api/docentes/${datos.id}`, {
            nombreCompleto: datos.nombreCompleto,
            password: datos.password || null,
        });
        avisar('Docente actualizado.', 'exito');
    } else {
        await api('POST', '/api/docentes', {
            username: datos.username,
            password: datos.password,
            nombreCompleto: datos.nombreCompleto,
        });
        avisar(`Docente creado. Ya puede ingresar con el usuario "${datos.username.trim()}".`, 'exito');
    }
    await cargarDocentes();
}

// ======================= Carreras =======================

async function cargarCarreras() {
    estado.carreras = await api('GET', '/api/carreras');
    const filas = estado.carreras.map((carrera) => el('tr', {},
        el('td', {}, carrera.codigo),
        el('td', {}, carrera.nombre),
        el('td', { class: 'num' }, carrera.materias),
        el('td', { class: 'num' }, carrera.estudiantes),
        el('td', { class: 'acciones' },
            botonChico('Editar', () => entrarEnEdicion($('#form-carrera'),
                { id: carrera.id, codigo: carrera.codigo, nombre: carrera.nombre }, `Editar ${carrera.nombre}`)),
            botonChico('Eliminar', () => eliminar(`/api/carreras/${carrera.id}`,
                `la carrera ${carrera.nombre}`, cargarCarreras), true))));
    dibujarFilas('#tabla-carreras', filas, 5, 'No hay carreras cargadas.');
}

async function guardarCarrera(datos) {
    const cuerpo = { codigo: datos.codigo, nombre: datos.nombre };
    if (datos.id) {
        await api('PUT', `/api/carreras/${datos.id}`, cuerpo);
        avisar('Carrera actualizada.', 'exito');
    } else {
        await api('POST', '/api/carreras', cuerpo);
        avisar('Carrera creada.', 'exito');
    }
    await cargarCarreras();
}

// ======================= Usuarios =======================

async function cargarUsuarios() {
    const usuarios = await api('GET', '/api/usuarios');
    const filas = usuarios.map((usuario) => el('tr', {},
        el('td', {}, usuario.username),
        el('td', {}, NOMBRES_ROL[usuario.rol] || usuario.rol),
        celdaEstado(usuario.activo),
        el('td', { class: 'acciones' },
            botonChico('Cambiar contraseña', () => cambiarContrasena(usuario)),
            usuario.activo
                ? botonChico('Desactivar', () => cambiarUsuario(usuario, { activo: false },
                    'Usuario desactivado: ya no puede ingresar.'), true)
                : botonChico('Activar', () => cambiarUsuario(usuario, { activo: true }, 'Usuario activado.')))));
    dibujarFilas('#tabla-usuarios', filas, 4, 'No hay usuarios.');
}

async function cambiarUsuario(usuario, cambios, mensaje) {
    try {
        await api('PATCH', `/api/usuarios/${usuario.id}`, cambios);
        avisar(mensaje, 'exito');
        await cargarUsuarios();
    } catch (error) {
        avisar(error.message, 'error');
    }
}

function cambiarContrasena(usuario) {
    const nueva = prompt(`Nueva contraseña para ${usuario.username} (mínimo 6 caracteres):`);
    if (nueva === null) return;
    if (nueva.length < 6) {
        avisar('La contraseña debe tener al menos 6 caracteres.', 'error');
        return;
    }
    cambiarUsuario(usuario, { password: nueva }, 'Contraseña cambiada.');
}

async function guardarUsuario(datos) {
    await api('POST', '/api/usuarios', { username: datos.username, password: datos.password });
    avisar(`Administrador "${datos.username.trim()}" creado.`, 'exito');
    await cargarUsuarios();
}

// ======================= Mis notas (estudiante) =======================

async function cargarMisNotas() {
    const notas = await api('GET', '/api/estudiantes/me/calificaciones');
    const filas = notas.map((nota) => el('tr', {},
        el('td', {}, nota.nombreMateria),
        el('td', {}, nota.codigoMateria),
        el('td', {}, nota.docenteNombre || '—'),
        el('td', { class: 'num' }, nota.semestre),
        el('td', { class: 'num' }, nota.creditos),
        el('td', { class: 'num nota' }, nota.nota),
        el('td', {}, formatearFecha(nota.fechaActualizacion))));
    dibujarFilas('#tabla-mis-notas', filas, 7, 'Todavía no tiene notas cargadas.');

    $('#resumen-materias').textContent = notas.length;
    $('#resumen-promedio').textContent = notas.length
        ? (notas.reduce((suma, nota) => suma + nota.nota, 0) / notas.length)
            .toLocaleString('es-PY', { maximumFractionDigits: 1 })
        : '–';
}

// ======================= Inicio =======================

$('#form-login').addEventListener('submit', ingresar);
$('#boton-salir').addEventListener('click', () => cerrarSesion());

configurarFormulario($('#form-nota'), guardarNota);
configurarFormulario($('#form-estudiante'), guardarEstudiante);
configurarFormulario($('#form-materia'), guardarMateria);
configurarFormulario($('#form-docente'), guardarDocente);
configurarFormulario($('#form-carrera'), guardarCarrera);
configurarFormulario($('#form-usuario'), guardarUsuario);

$('#form-nota').elements.materiaId.addEventListener('change', actualizarEstudiantesDeNota);
// reset() limpia los valores después del evento: se actualiza la lista en la vuelta siguiente
$('#form-nota').addEventListener('reset', () => setTimeout(actualizarEstudiantesDeNota));

const avisarError = (error) => avisar(error.message, 'error');
$('#filtro-nota-materia').addEventListener('change', () => cargarNotas().catch(avisarError));
$('#filtro-nota-estudiante').addEventListener('change', () => cargarNotas().catch(avisarError));
$('#filtro-estudiante-carrera').addEventListener('change', () => cargarEstudiantes().catch(avisarError));
$('#filtro-materia-carrera').addEventListener('change', () => cargarMaterias().catch(avisarError));

estado.sesion = leerSesion();
if (estado.sesion) mostrarApp();
else $('#vista-login').hidden = false;
