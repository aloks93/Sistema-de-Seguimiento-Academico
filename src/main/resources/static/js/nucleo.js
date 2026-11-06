// Estado de la sesión, comunicación con la API y utilidades generales.

export const estado = {
    sesion: null, // { token, username, rol }
    perfil: null, // { username, rol, nombre, preferencias, dosFactores, superAdmin }
};

export const esAdmin = () => estado.sesion?.rol === 'ADMIN';
export const esDocente = () => estado.sesion?.rol === 'DOCENTE';
/** El único administrador que puede eliminar o modificar a otros administradores. */
export const esSuperAdmin = () => esAdmin() && Boolean(estado.perfil?.superAdmin);
export const $ = (selector) => document.querySelector(selector);

// ======================= Sesión =======================
// El token se guarda en sessionStorage: la sesión termina al cerrar la pestaña.

export function leerSesion() {
    try {
        return JSON.parse(sessionStorage.getItem('sesion'));
    } catch {
        return null;
    }
}

export function guardarSesion(sesion) {
    try {
        if (sesion) sessionStorage.setItem('sesion', JSON.stringify(sesion));
        else sessionStorage.removeItem('sesion');
    } catch {
        // Sin almacenamiento disponible: la sesión dura hasta recargar la página
    }
}

// ======================= API =======================

const NOMBRES_CAMPO = {
    nota: 'Nota', codigo: 'Código', nombre: 'Nombre', creditos: 'Créditos', semestre: 'Semestre',
    matricula: 'Matrícula', nombreCompleto: 'Nombre completo', carreraId: 'Carrera', docenteId: 'Docente',
    username: 'Usuario', password: 'Contraseña', actual: 'Contraseña actual', nueva: 'Contraseña nueva',
    colorPrimario: 'Color principal', colorBarra: 'Color de la barra', semestres: 'Semestres',
    primerParcial: '1er parcial (0 a 15)', segundoParcial: '2do parcial (0 a 15)', trabajoPractico: 'Trabajo práctico (0 a 20)',
    examenFinal: 'Examen final (0 a 50)', asistenciaMinima: 'Asistencia mínima', clasesPrevistas: 'Clases previstas',
    fecha: 'Fecha', tema: 'Tema', motivo: 'Motivo', estudianteIds: 'Estudiantes', registros: 'Lista',
};

export class ErrorApi extends Error {
    constructor(status, problema) {
        super(mensajeDeError(status, problema));
        this.status = status;
        this.problema = problema; // respuesta completa del servidor (p. ej. forzable al eliminar)
    }
}

/** Mensaje legible a partir de la respuesta de error (RFC 7807) del servidor. */
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

let alExpirar = () => {};
/** Lo que se hace cuando el servidor rechaza el token (sesión vencida). */
export function alExpirarSesion(funcion) {
    alExpirar = funcion;
}

// Peticiones en curso: mientras haya alguna se muestra la barra de progreso superior
let pendientes = 0;
function marcarCarga(delta) {
    pendientes = Math.max(0, pendientes + delta);
    document.documentElement.classList.toggle('cargando', pendientes > 0);
}

const esJson = (respuesta) => (respuesta.headers.get('Content-Type') || '').includes('json');

/** Petición autenticada. Devuelve la respuesta si es exitosa; si no, lanza un error con mensaje legible. */
async function pedir(metodo, ruta, { json, formulario } = {}) {
    const opciones = { method: metodo, headers: {} };
    if (estado.sesion) opciones.headers.Authorization = 'Bearer ' + estado.sesion.token;
    if (json !== undefined) {
        opciones.headers['Content-Type'] = 'application/json';
        opciones.body = JSON.stringify(json);
    } else if (formulario) {
        opciones.body = formulario; // el navegador pone el Content-Type multipart
    }

    marcarCarga(+1);
    try {
        let respuesta;
        try {
            respuesta = await fetch(ruta, opciones);
        } catch {
            throw new Error('No se pudo conectar con el servidor. ¿Sigue abierta la ventana del backend (iniciar-todo.cmd)?');
        }
        if (respuesta.status === 401 && estado.sesion) {
            alExpirar();
            throw new Error('La sesión expiró. Ingrese de nuevo.');
        }
        if (!respuesta.ok) throw new ErrorApi(respuesta.status, esJson(respuesta) ? await respuesta.json() : null);
        return respuesta;
    } finally {
        marcarCarga(-1);
    }
}

export async function api(metodo, ruta, cuerpo) {
    const respuesta = await pedir(metodo, ruta, { json: cuerpo });
    return esJson(respuesta) ? respuesta.json() : null;
}

/** Descarga un archivo (Excel) y lo guarda con el nombre que indica el servidor. Devuelve ese nombre. */
export async function descargar(ruta) {
    const respuesta = await pedir('GET', ruta);
    const disposicion = respuesta.headers.get('Content-Disposition') || '';
    const nombre = decodeURIComponent(disposicion.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)?.[1] ?? 'archivo.xlsx');
    const enlace = document.createElement('a');
    enlace.href = URL.createObjectURL(await respuesta.blob());
    enlace.download = nombre;
    document.body.append(enlace);
    enlace.click();
    enlace.remove();
    setTimeout(() => URL.revokeObjectURL(enlace.href), 2000);
    return nombre;
}

/** Sube un archivo en el campo "archivo" y devuelve la respuesta JSON. */
export async function subirArchivo(ruta, archivo) {
    const formulario = new FormData();
    formulario.append('archivo', archivo);
    return (await pedir('POST', ruta, { formulario })).json();
}

// ======================= DOM =======================

/**
 * Crea un elemento. Los textos se insertan siempre como texto (nunca como HTML).
 * Atributos: class, on<evento>, value; true = atributo vacío; false/null/undefined = se omite.
 */
export function el(etiqueta, atributos = {}, ...hijos) {
    const nodo = document.createElement(etiqueta);
    for (const [clave, valor] of Object.entries(atributos)) {
        if (valor === false || valor === null || valor === undefined) continue;
        if (clave === 'class') nodo.className = valor;
        else if (clave.startsWith('on')) nodo.addEventListener(clave.slice(2), valor);
        else if (clave === 'value') nodo.value = valor;
        else nodo.setAttribute(clave, valor === true ? '' : valor);
    }
    nodo.append(...hijos.flat(Infinity).filter((hijo) => hijo !== null && hijo !== undefined && hijo !== false));
    return nodo;
}

// ======================= Formato =======================

export function formatearFecha(iso) {
    return iso ? new Date(iso).toLocaleDateString('es-PY', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '';
}

export function formatearNumero(numero, decimales = 1) {
    return numero === null || numero === undefined || Number.isNaN(numero)
        ? '–'
        : numero.toLocaleString('es-PY', { maximumFractionDigits: decimales });
}

export function promedio(valores) {
    return valores.length ? valores.reduce((suma, valor) => suma + valor, 0) / valores.length : null;
}

export function iniciales(nombre = '') {
    const palabras = nombre.trim().split(/\s+/).filter((palabra) => /\p{L}/u.test(palabra));
    return ((palabras[0]?.[0] || '') + (palabras.length > 1 ? palabras[palabras.length - 1][0] : '')).toUpperCase() || '?';
}

/** Texto en minúsculas y sin tildes, para búsquedas. */
export function normalizar(texto) {
    return String(texto).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}
