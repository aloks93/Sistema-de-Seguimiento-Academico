// Interfaz web del Sistema de Seguimiento Académico: navegación y pantallas.
//
// Roles:
//   ADMIN      gestiona carreras, docentes, materias, estudiantes, usuarios y todas las notas
//   DOCENTE    estudiantes de sus carreras y notas de las materias que tiene a cargo
//   ESTUDIANTE sus propias notas
// Todos tienen "Ajustes" para personalizar la apariencia y cambiar su contraseña.

import {
    $, api, descargar, el, estado, esAdmin, formatearFecha, formatearNumero, guardarSesion,
    leerSesion, alExpirarSesion, normalizar, promedio, subirArchivo,
} from './nucleo.js';
import {
    abrirFormulario, avatar, avisar, barraAvance, barraHerramientas, boton, botonIcono, buscador, chip,
    cerrarModal, confirmar, esqueleto, estadoVacio, icono, panel, persona, selectorFiltro, tabla, tarjetaDato,
} from './ui.js';
import {
    COLORES_BARRA, COLORES_PRIMARIOS, TEMA_POR_DEFECTO, aplicarTema, recordarTema, temaActual, temaRecordado,
} from './tema.js';

const NOMBRES_ROL = { ADMIN: 'Administrador', DOCENTE: 'Docente', ESTUDIANTE: 'Estudiante' };

// ======================= Rutas =======================

const TODOS = ['ADMIN', 'DOCENTE', 'ESTUDIANTE'];
const GESTION = ['ADMIN', 'DOCENTE'];

const RUTAS = {
    inicio: {
        titulo: 'Inicio', icono: 'inicio', roles: GESTION, render: vistaInicio,
        subtitulo: () => `Hola, ${estado.perfil?.nombre ?? estado.sesion.username}. Este es el resumen de ${esAdmin() ? 'la institución' : 'sus materias'}.`,
    },
    misNotas: { titulo: 'Mis notas', icono: 'notas', roles: ['ESTUDIANTE'], render: vistaMisNotas, subtitulo: 'Sus calificaciones y su promedio' },
    misAsistencias: {
        titulo: 'Mi asistencia', icono: 'asistencia', roles: ['ESTUDIANTE'], render: vistaMiAsistencia,
        subtitulo: 'Su asistencia en cada materia y si puede rendir el examen final',
    },
    notas: { titulo: 'Notas', icono: 'notas', roles: GESTION, render: vistaNotas, subtitulo: 'Planilla de notas por materia: parciales, trabajo práctico y examen final' },
    asistencia: {
        titulo: 'Asistencia', icono: 'asistencia', roles: GESTION, render: vistaAsistencia,
        subtitulo: 'Pasar lista, ver el porcentaje de cada estudiante y quién está habilitado para el final',
    },
    reportes: {
        titulo: 'Reportes', icono: 'grafico', roles: GESTION, render: vistaReportes,
        subtitulo: () => (esAdmin() ? 'Asistencia de toda la institución' : 'Asistencia de sus materias'),
    },
    estudiantes: {
        titulo: 'Estudiantes', icono: 'estudiantes', roles: GESTION, render: vistaEstudiantes,
        subtitulo: () => (esAdmin() ? 'Todos los estudiantes de la institución' : 'Estudiantes de las carreras donde tiene materias'),
    },
    materias: {
        titulo: () => (esAdmin() ? 'Materias' : 'Mis materias'), icono: 'materias', roles: GESTION, render: vistaMaterias,
        subtitulo: () => (esAdmin() ? 'Materias de cada carrera y su docente a cargo' : 'Materias que tiene a cargo'),
    },
    docentes: { titulo: 'Docentes', icono: 'docentes', roles: ['ADMIN'], render: vistaDocentes, subtitulo: 'Profesores y las materias que tienen a cargo' },
    carreras: { titulo: 'Carreras', icono: 'carreras', roles: ['ADMIN'], render: vistaCarreras, subtitulo: 'Carreras de la institución' },
    cargaMasiva: {
        titulo: 'Carga masiva', icono: 'subir', roles: ['ADMIN'], render: vistaCargaMasiva,
        subtitulo: 'Cargue docentes, materias y estudiantes de una carrera con una plantilla Excel',
    },
    exoneraciones: {
        titulo: 'Exoneraciones', icono: 'escudo', roles: ['ADMIN'], render: vistaExoneraciones,
        subtitulo: 'Exoneraciones de asistencia por causa justificada (hasta 2 materias por semestre)',
    },
    usuarios: { titulo: 'Usuarios', icono: 'usuarios', roles: ['ADMIN'], render: vistaUsuarios, subtitulo: 'Cuentas de acceso, activación, contraseñas y verificación en dos pasos' },
    ajustes: { titulo: 'Ajustes', icono: 'ajustes', roles: TODOS, render: vistaAjustes, subtitulo: 'Apariencia, seguridad y datos de su cuenta' },
};
const MENU_PRINCIPAL = ['inicio', 'misNotas', 'misAsistencias', 'notas', 'asistencia', 'reportes', 'estudiantes',
    'materias', 'docentes', 'carreras', 'exoneraciones', 'cargaMasiva', 'usuarios'];

// ======================= Asistencia y notas: utilidades =======================

/** Condición de un estudiante para el examen final según su asistencia (ver CalculoAsistencia). */
const CONDICIONES = {
    HABILITADO: ['Habilitado', 'exito'],
    EN_RIESGO: ['En riesgo', 'aviso'],
    NO_HABILITADO: ['No habilitado', 'peligro'],
    EXONERADO: ['Exonerado', 'primario'],
    SIN_REGISTROS: ['Sin clases', 'neutro'],
};
const chipCondicion = (condicion) => chip(...(CONDICIONES[condicion] ?? [condicion, 'neutro']));

const ESTADOS_ASISTENCIA = [
    { valor: 'PRESENTE', letra: 'P', texto: 'Presente', plural: 'presentes', tipo: 'exito' },
    { valor: 'TARDE', letra: 'T', texto: 'Tarde', plural: 'tarde', tipo: 'aviso' },
    { valor: 'AUSENTE', letra: 'A', texto: 'Ausente', plural: 'ausentes', tipo: 'peligro' },
    { valor: 'JUSTIFICADO', letra: 'J', texto: 'Justificada', plural: 'justificadas', tipo: 'primario' },
];
function chipEstadoAsistencia(estado) {
    const datos = ESTADOS_ASISTENCIA.find((e) => e.valor === estado);
    return datos ? chip(datos.texto, datos.tipo) : chip('Sin registrar');
}

const dosCifras = (numero) => String(numero).padStart(2, '0');
/** Fecha de hoy en formato AAAA-MM-DD (hora local). */
function hoyIso() {
    const hoy = new Date();
    return `${hoy.getFullYear()}-${dosCifras(hoy.getMonth() + 1)}-${dosCifras(hoy.getDate())}`;
}
/** Fecha AAAA-MM-DD (sin hora) legible, sin corrimientos por zona horaria. */
function formatearDia(iso, conDiaSemana = true) {
    if (!iso) return '';
    const [anio, mes, dia] = iso.split('-').map(Number);
    return new Date(anio, mes - 1, dia).toLocaleDateString('es-PY', {
        ...(conDiaSemana ? { weekday: 'short' } : {}), day: '2-digit', month: '2-digit', year: 'numeric',
    });
}

/** exito: holgado · aviso: a menos de 10 puntos del mínimo · peligro: debajo del mínimo. */
function tonoAsistencia(porcentaje, minimo) {
    if (porcentaje === null || porcentaje === undefined || minimo === null || minimo === undefined) return 'primario';
    if (porcentaje < minimo) return 'peligro';
    if (porcentaje < Math.min(minimo + 10, 100)) return 'aviso';
    return 'exito';
}
const textoPorcentaje = (porcentaje) => (porcentaje === null || porcentaje === undefined ? '–' : `${formatearNumero(porcentaje)} %`);

/** Barra de asistencia con una marca en el mínimo requerido. */
function medidorAsistencia(porcentaje, minimo) {
    return el('div', {
        class: `medidor medidor--${tonoAsistencia(porcentaje, minimo)}`,
        title: minimo === null || minimo === undefined ? null : `Mínimo para rendir el final: ${minimo} %`,
    },
    el('span', { class: 'medidor__valor medidor__valor--porcentaje' }, textoPorcentaje(porcentaje)),
    el('div', { class: 'medidor__pista' },
        el('div', { class: 'medidor__relleno', style: `width: ${porcentaje ?? 0}%` }),
        minimo !== null && minimo !== undefined && el('div', { class: 'medidor__minimo', style: `left: ${minimo}%` })));
}

/** Escala del reglamento: 1-59 = 1, 60-69 = 2, 70-79 = 3, 80-90 = 4, 91-100 = 5. */
function escala(puntaje) {
    if (puntaje < 60) return 1;
    if (puntaje < 70) return 2;
    if (puntaje < 80) return 3;
    if (puntaje <= 90) return 4;
    return 5;
}
function chipCalificacion(calificacion) {
    if (calificacion === null || calificacion === undefined) return chip('En curso');
    return calificacion === 1 ? chip('1 · Reprobado', 'peligro') : chip(`${calificacion} · Aprobado`, 'exito');
}

/** Componentes de la nota (reglamento UPE): suman 100 puntos. */
const COMPONENTES_NOTA = [
    { clave: 'primerParcial', titulo: '1er parcial', maximo: 15 },
    { clave: 'segundoParcial', titulo: '2do parcial', maximo: 15 },
    { clave: 'trabajoPractico', titulo: 'T. práctico', maximo: 20 },
    { clave: 'examenFinal', titulo: 'Examen final', maximo: 50 },
];

/** Botón que descarga un Excel generado por el servidor. */
function botonExcel(texto, ruta, variante = 'secundario') {
    const nuevo = boton(texto, async () => {
        nuevo.disabled = true;
        nuevo.classList.add('boton--ocupado');
        try {
            avisar(`Descargado: ${await descargar(ruta)}`, 'exito');
        } catch (error) {
            avisar(error.message, 'error');
        } finally {
            nuevo.disabled = false;
            nuevo.classList.remove('boton--ocupado');
        }
    }, { variante, iconoNombre: 'descargar', titulo: 'Descargar en Excel' });
    return nuevo;
}

const resolver = (valor) => (typeof valor === 'function' ? valor() : valor);
const rutasDelRol = (ids) => ids.filter((id) => RUTAS[id].roles.includes(estado.sesion.rol));
const ir = (ruta) => { location.hash = '#/' + ruta; };
const conParametro = (base, nombre, valor) => (valor ? `${base}?${nombre}=${encodeURIComponent(valor)}` : base);

function leerRuta() {
    const [id, consulta = ''] = location.hash.replace(/^#\/?/, '').split('?');
    return { id, parametros: new URLSearchParams(consulta) };
}

// Pantallas con cambios sin guardar (planilla de notas) registran aquí cuántos tienen
let cambiosSinGuardar = () => 0;
let ignorarCambioRuta = false;
let turnoDeRender = 0;
const busquedas = new Map();

function ponerAcciones(...nodos) {
    $('#acciones-pagina').replaceChildren(...nodos.filter(Boolean));
}

function animarEntrada(nodo) {
    nodo.classList.remove('entrando');
    void nodo.offsetWidth; // reinicia la animación
    nodo.classList.add('entrando');
}

async function puedeSalir() {
    const pendientes = cambiosSinGuardar();
    if (!pendientes) return true;
    const salir = await confirmar({
        titulo: 'Hay cambios sin guardar',
        mensaje: `Tiene ${pendientes === 1 ? '1 cambio' : pendientes + ' cambios'} sin guardar. Si sale ahora se perderán.`,
        textoBoton: 'Salir sin guardar', peligro: true,
    });
    if (salir) cambiosSinGuardar = () => 0;
    return salir;
}

async function alCambiarRuta(evento) {
    if (ignorarCambioRuta) {
        ignorarCambioRuta = false;
        return;
    }
    if (!(await puedeSalir())) {
        ignorarCambioRuta = true;
        location.hash = new URL(evento.oldURL).hash;
        return;
    }
    navegar();
}

/** Muestra la pantalla de la ruta actual. Con suave = true no muestra el esqueleto de carga (recargas). */
async function navegar({ suave = false } = {}) {
    if (!estado.sesion) return;
    const { id, parametros } = leerRuta();
    const ruta = RUTAS[id];
    if (!ruta || !ruta.roles.includes(estado.sesion.rol)) {
        location.replace('#/' + rutasDelRol(MENU_PRINCIPAL)[0]);
        return;
    }

    document.body.classList.remove('menu-abierto');
    for (const enlace of document.querySelectorAll('.nav__item')) {
        const activo = enlace.dataset.ruta === id;
        enlace.classList.toggle('activo', activo);
        if (activo) enlace.setAttribute('aria-current', 'page');
        else enlace.removeAttribute('aria-current');
    }
    const titulo = resolver(ruta.titulo);
    document.title = `${titulo} · Seguimiento Académico`;
    $('#titulo-pagina').textContent = titulo;
    $('#subtitulo-pagina').textContent = resolver(ruta.subtitulo) ?? '';

    const contenido = $('#contenido');
    const turno = ++turnoDeRender;
    if (!suave) {
        cambiosSinGuardar = () => 0;
        ponerAcciones();
        contenido.replaceChildren(esqueleto());
    }
    try {
        const vista = await ruta.render(parametros);
        if (turno !== turnoDeRender) return;
        contenido.replaceChildren(vista);
        if (!suave) {
            animarEntrada(contenido);
            $('#principal').scrollTo({ top: 0 });
        }
    } catch (error) {
        if (turno !== turnoDeRender || !estado.sesion) return;
        contenido.replaceChildren(estadoVacio({
            iconoNombre: 'alerta', titulo: 'No se pudo cargar esta pantalla', texto: error.message,
            accion: boton('Reintentar', () => navegar(), { variante: 'primario' }),
        }));
    }
}

const recargar = () => navegar({ suave: true });

// ======================= Sesión =======================

/** Muestra el paso de usuario y contraseña, o el del código de verificación (2FA). */
function pasoDeLogin(conCodigo) {
    const formulario = $('#form-login');
    $('#login-credenciales').hidden = conCodigo;
    $('#login-codigo').hidden = !conCodigo;
    $('#login-volver').hidden = !conCodigo;
    $('#login-boton-texto').textContent = conCodigo ? 'Verificar' : 'Ingresar';
    formulario.elements.codigo.required = conCodigo;
    formulario.elements.codigo.value = '';
    $('#login-error').hidden = true;
    (conCodigo ? formulario.elements.codigo : formulario.elements.username).focus();
}

async function ingresar(evento) {
    evento.preventDefault();
    const formulario = evento.currentTarget;
    const botonIngresar = formulario.querySelector('[type=submit]');
    const error = $('#login-error');
    error.hidden = true;
    botonIngresar.disabled = true;
    botonIngresar.classList.add('boton--ocupado');
    try {
        const { username, password, codigo } = Object.fromEntries(new FormData(formulario));
        const conCodigo = !$('#login-codigo').hidden;
        const respuesta = await api('POST', '/api/auth/login',
            { username: username.trim(), password, codigo: conCodigo ? codigo.trim() : null });
        if (respuesta.requiereCodigo) {
            pasoDeLogin(true);
            return;
        }
        estado.sesion = { token: respuesta.token, username: respuesta.username, rol: respuesta.rol };
        guardarSesion(estado.sesion);
        formulario.reset();
        pasoDeLogin(false);
        await iniciarApp();
    } catch (excepcion) {
        error.replaceChildren(icono('alerta'), el('span', {}, excepcion.message));
        error.hidden = false;
        if (!$('#login-codigo').hidden) formulario.elements.codigo.select();
    } finally {
        botonIngresar.disabled = false;
        botonIngresar.classList.remove('boton--ocupado');
    }
}

function mostrarLogin() {
    aplicarTema(TEMA_POR_DEFECTO);
    $('#vista-app').hidden = true;
    $('#vista-login').hidden = false;
    animarEntrada($('#vista-login'));
    pasoDeLogin(false);
}

function cerrarSesion(mensaje) {
    estado.sesion = null;
    estado.perfil = null;
    cambiosSinGuardar = () => 0;
    guardarSesion(null);
    cerrarModal();
    history.replaceState(null, '', location.pathname);
    mostrarLogin();
    if (mensaje) avisar(mensaje);
}

async function iniciarApp() {
    const { username } = estado.sesion;
    aplicarTema(temaRecordado(username) ?? TEMA_POR_DEFECTO);
    $('#vista-login').hidden = true;
    $('#vista-app').hidden = false;
    dibujarMenu();
    try {
        estado.perfil = await api('GET', '/api/perfil');
        const preferencias = estado.perfil.preferencias ?? TEMA_POR_DEFECTO;
        aplicarTema(preferencias);
        recordarTema(username, preferencias);
        dibujarMenu();
    } catch (error) {
        if (!estado.sesion) return;
        avisar('No se pudo cargar el perfil: ' + error.message, 'error');
    }
    const { id } = leerRuta();
    if (RUTAS[id]?.roles.includes(estado.sesion.rol)) navegar();
    else location.replace('#/' + rutasDelRol(MENU_PRINCIPAL)[0]);
}

function dibujarMenu() {
    const enlace = (id) => el('a', { href: '#/' + id, class: 'nav__item', 'data-ruta': id },
        icono(RUTAS[id].icono), el('span', {}, resolver(RUTAS[id].titulo)));
    $('#navegacion').replaceChildren(
        el('span', { class: 'nav__grupo' }, 'Menú'),
        ...rutasDelRol(MENU_PRINCIPAL).map(enlace),
        el('span', { class: 'nav__grupo' }, 'Cuenta'),
        enlace('ajustes'));

    const nombre = estado.perfil?.nombre ?? estado.sesion.username;
    $('#tarjeta-usuario').replaceChildren(
        avatar(nombre, 'avatar--lateral'),
        el('div', { class: 'lateral__usuario-textos' },
            el('span', { class: 'lateral__usuario-nombre' }, nombre),
            el('span', { class: 'lateral__usuario-rol' }, NOMBRES_ROL[estado.sesion.rol])));
    const { id } = leerRuta();
    document.querySelector(`.nav__item[data-ruta="${id}"]`)?.classList.add('activo');
}

// ======================= Inicio (panel) =======================

async function vistaInicio() {
    const peticionReporte = api('GET', '/api/asistencia/reporte');
    const peticiones = [api('GET', '/api/materias'), api('GET', '/api/estudiantes'), api('GET', '/api/calificaciones')];
    if (esAdmin()) peticiones.push(api('GET', '/api/carreras'), api('GET', '/api/docentes'));
    const [materias, estudiantes, notas, carreras, docentes] = await Promise.all(peticiones);
    const reporte = await peticionReporte;
    ponerAcciones(botonExcel(esAdmin() ? 'Exportar todas las notas' : 'Exportar mis notas cargadas', '/api/exportar/notas'));

    const promedioGeneral = promedio(notas.map((nota) => nota.nota));
    const sinDocente = materias.filter((materia) => !materia.docenteId).length;
    const tarjetaAsistencia = tarjetaDato({
        iconoNombre: 'asistencia', titulo: 'Asistencia promedio', valor: textoPorcentaje(reporte.promedioAsistencia),
        detalle: `${reporte.clases} ${reporte.clases === 1 ? 'clase registrada' : 'clases registradas'}`,
    });
    const tarjetas = esAdmin()
        ? [
            tarjetaDato({ iconoNombre: 'carreras', titulo: 'Carreras', valor: carreras.length }),
            tarjetaDato({
                iconoNombre: 'materias', titulo: 'Materias', valor: materias.length,
                detalle: sinDocente ? `${sinDocente} sin docente asignado` : 'Todas con docente',
            }),
            tarjetaDato({ iconoNombre: 'docentes', titulo: 'Docentes', valor: docentes.length }),
            tarjetaDato({ iconoNombre: 'estudiantes', titulo: 'Estudiantes', valor: estudiantes.length }),
            tarjetaDato({ iconoNombre: 'notas', titulo: 'Notas cargadas', valor: notas.length }),
            tarjetaDato({ iconoNombre: 'grafico', titulo: 'Promedio general', valor: formatearNumero(promedioGeneral) }),
            tarjetaAsistencia,
        ]
        : [
            tarjetaDato({ iconoNombre: 'materias', titulo: 'Mis materias', valor: materias.length }),
            tarjetaDato({ iconoNombre: 'estudiantes', titulo: 'Mis estudiantes', valor: estudiantes.length }),
            tarjetaDato({ iconoNombre: 'notas', titulo: 'Notas cargadas', valor: notas.length }),
            tarjetaDato({ iconoNombre: 'grafico', titulo: 'Promedio', valor: formatearNumero(promedioGeneral) }),
            tarjetaAsistencia,
        ];

    const avance = materias.map((materia) => {
        const notasDeMateria = notas.filter((nota) => nota.materiaId === materia.id);
        return {
            materia,
            total: materia.inscriptos,
            cargadas: notasDeMateria.length,
            promedio: promedio(notasDeMateria.map((nota) => nota.nota)),
        };
    });

    const columnas = [
        { titulo: 'Materia', celda: ({ materia }) => el('div', { class: 'celda-doble' },
            el('span', {}, `${materia.codigo} · ${materia.nombre}`),
            el('span', { class: 'sutil' }, `${materia.carreraNombre} · ${materia.semestre}° semestre`)) },
        esAdmin() && { titulo: 'Docente', celda: ({ materia }) => materia.docenteNombre ?? chip('Sin asignar', 'aviso') },
        { titulo: 'Notas cargadas', clase: 'columna-avance', celda: ({ cargadas, total }) => barraAvance(cargadas, total) },
        { titulo: 'Promedio', clase: 'num', celda: (fila) => formatearNumero(fila.promedio) },
        { titulo: '', clase: 'acciones', celda: ({ materia }) => [
            boton('Asistencia', () => ir(`asistencia?materia=${materia.id}`), { variante: 'fantasma', iconoNombre: 'asistencia' }),
            boton('Notas', () => ir(`notas?materia=${materia.id}`), { variante: 'fantasma', iconoNombre: 'flecha' }),
        ] },
    ].filter(Boolean);

    const vacio = esAdmin()
        ? estadoVacio({ iconoNombre: 'materias', titulo: 'Todavía no hay materias', texto: 'Empiece creando las carreras, los docentes y las materias.',
            accion: boton('Ir a Carreras', () => ir('carreras'), { variante: 'primario' }) })
        : estadoVacio({ iconoNombre: 'materias', titulo: 'Todavía no tiene materias a cargo', texto: 'Pida al administrador que le asigne sus materias.' });

    const alertas = reporte.alertas.slice(0, 5);
    const panelAlertas = alertas.length > 0 && panel({
        titulo: 'Alertas de asistencia',
        descripcion: `${reporte.noHabilitados} no habilitados y ${reporte.enRiesgo} en riesgo de perder el derecho al examen final.`,
        acciones: boton('Ver reportes', () => ir('reportes'), { variante: 'fantasma', iconoNombre: 'flecha' }),
        contenido: tablaAlertas(alertas),
    });

    return el('div', { class: 'pila' },
        el('div', { class: 'tarjetas' }, tarjetas),
        panelAlertas,
        panel({
            titulo: 'Avance de carga de notas',
            descripcion: 'Estudiantes con nota sobre el total de inscriptos de cada materia.',
            contenido: tabla({ columnas, filas: avance, vacio }),
        }));
}

/** Estudiantes en riesgo o no habilitados por asistencia (panel de inicio y reportes). */
function tablaAlertas(alertas) {
    return tabla({
        columnas: [
            { titulo: 'Estudiante', celda: (a) => persona(a.nombreEstudiante, `Matrícula ${a.matricula}`) },
            { titulo: 'Materia', celda: (a) => el('div', { class: 'celda-doble' },
                el('span', {}, a.nombreMateria), el('span', { class: 'sutil' }, a.codigoMateria)) },
            { titulo: 'Asistencia', clase: 'columna-avance', celda: (a) => medidorAsistencia(a.porcentaje, a.asistenciaMinima) },
            { titulo: 'Faltas restantes', clase: 'num', celda: (a) => a.faltasRestantes ?? '–' },
            { titulo: 'Condición', celda: (a) => chipCondicion(a.condicion) },
            { titulo: '', clase: 'acciones', celda: (a) =>
                boton('Ver materia', () => ir(`asistencia?materia=${a.materiaId}&ver=resumen`), { variante: 'fantasma', iconoNombre: 'flecha' }) },
        ],
        filas: alertas,
        vacio: estadoVacio({ iconoNombre: 'check', titulo: 'Nadie en riesgo', texto: 'Todos los estudiantes con clases registradas superan el mínimo con holgura.' }),
    });
}

// ======================= Notas (planilla por materia) =======================

async function vistaNotas(parametros) {
    const materias = await api('GET', '/api/materias');
    if (!materias.length) {
        return esAdmin()
            ? estadoVacio({ iconoNombre: 'materias', titulo: 'Todavía no hay materias', texto: 'Cree las carreras y materias para poder cargar notas.',
                accion: boton('Ir a Materias', () => ir('materias'), { variante: 'primario' }) })
            : estadoVacio({ iconoNombre: 'notas', titulo: 'Todavía no tiene materias a cargo',
                texto: 'Pida al administrador que le asigne sus materias para poder cargar notas.' });
    }

    const materia = materias.find((m) => String(m.id) === parametros.get('materia'));
    const selector = selectorMateria(materias, materia?.id, 'notas');
    if (!materia) {
        return el('div', { class: 'pila' },
            panel({ contenido: selector, clase: 'panel--selector' }),
            estadoVacio({ iconoNombre: 'notas', titulo: 'Elija una materia',
                texto: 'Se mostrará la planilla con los estudiantes inscriptos para cargar sus parciales, trabajo práctico y examen final.' }));
    }

    const [notas, inscriptos, asistencia] = await Promise.all([
        api('GET', `/api/calificaciones?materiaId=${materia.id}`),
        api('GET', `/api/inscripciones?materiaId=${materia.id}`),
        api('GET', `/api/asistencia/resumen?materiaId=${materia.id}`),
    ]);
    return planillaNotas(materia, inscriptos, notas, asistencia, selector);
}

function selectorMateria(materias, seleccionada, ruta = 'notas') {
    const porCarrera = new Map();
    for (const materia of materias) {
        if (!porCarrera.has(materia.carreraNombre)) porCarrera.set(materia.carreraNombre, []);
        porCarrera.get(materia.carreraNombre).push(materia);
    }
    const select = el('select', {
        class: 'selector-materia__select', 'aria-label': 'Materia',
        onchange: (evento) => ir(conParametro(ruta, 'materia', evento.target.value)),
    },
    el('option', { value: '' }, 'Elegir materia…'),
    [...porCarrera].map(([carrera, lista]) => el('optgroup', { label: carrera },
        lista.map((m) => el('option', { value: m.id },
            `${m.codigo} · ${m.nombre}${esAdmin() && m.docenteNombre ? ' — ' + m.docenteNombre : ''}`)))));
    select.value = seleccionada ?? '';
    return el('label', { class: 'selector-materia' }, el('span', { class: 'campo__etiqueta' }, 'Materia'), select);
}

const ESTADOS_NOTA = {
    guardada: ['Guardada', 'exito'],
    vacia: ['Sin nota', 'neutro'],
    modificada: ['Sin guardar', 'aviso'],
    invalida: ['Valor fuera de rango', 'peligro'],
    vaciada: ['Para borrarla use Eliminar', 'peligro'],
};

/**
 * Planilla de notas desglosada: dos parciales (15 + 15), trabajo práctico (20) y examen final (50).
 * El examen final solo se habilita para quien tiene la asistencia mínima (o está exonerado).
 */
function planillaNotas(materia, inscriptos, notas, asistencia, selector) {
    const porEstudiante = new Map(notas.map((nota) => [nota.estudianteId, nota]));
    const asistenciaDe = new Map(asistencia.estudiantes.map((fila) => [fila.estudianteId, fila]));
    const filas = [];

    const resumen = el('div', { class: 'resumen-materia' });
    const contador = el('span', {});
    const botonGuardar = boton('Guardar cambios', () => guardar(), { variante: 'primario', iconoNombre: 'guardar' });
    const barraGuardar = el('div', { class: 'barra-guardar', hidden: true },
        el('div', { class: 'barra-guardar__info' }, icono('info'), contador),
        el('div', { class: 'barra-guardar__acciones' },
            boton('Descartar', () => descartar(), { iconoNombre: 'deshacer' }), botonGuardar));

    const pendientes = () => filas.filter((fila) => fila.estado === 'modificada');
    cambiosSinGuardar = () => pendientes().length;

    const leer = (fila) => fila.entradas.map((entrada) => entrada.value.trim());
    const originales = (fila) => COMPONENTES_NOTA.map(({ clave }) =>
        (fila.nota?.desglosada && fila.nota[clave] !== null && fila.nota[clave] !== undefined ? String(fila.nota[clave]) : ''));

    function calcularEstado(fila) {
        const textos = leer(fila);
        const antes = originales(fila);
        if (textos.every((texto, i) => texto === antes[i])) return fila.nota ? 'guardada' : 'vacia';
        if (textos.every((texto) => texto === '')) return fila.nota?.desglosada ? 'vaciada' : (fila.nota ? 'guardada' : 'vacia');
        const validos = textos.every((texto, i) => {
            if (texto === '') return true;
            const valor = Number(texto);
            return Number.isInteger(valor) && valor >= 0 && valor <= COMPONENTES_NOTA[i].maximo;
        });
        return validos ? 'modificada' : 'invalida';
    }

    /** Puntaje total y si ya está completo (tiene examen final o es una nota única). */
    function total(fila) {
        const textos = leer(fila);
        if (textos.some((texto) => texto !== '')) {
            return { valor: textos.reduce((suma, texto) => suma + (Number(texto) || 0), 0), completa: textos[3] !== '' };
        }
        if (fila.nota) return { valor: fila.nota.nota, completa: fila.nota.calificacion !== null };
        return null;
    }

    function actualizarFila(fila) {
        fila.estado = calcularEstado(fila);
        fila.tr.dataset.estado = fila.estado;
        const puntaje = total(fila);
        fila.celdaTotal.textContent = puntaje ? puntaje.valor : '';
        fila.celdaCalificacion.replaceChildren(puntaje ? chipCalificacion(puntaje.completa ? escala(puntaje.valor) : null) : '');
        const notaUnica = fila.nota && !fila.nota.desglosada;
        fila.celdaEstado.replaceChildren(el('div', { class: 'chips' }, chip(...ESTADOS_NOTA[fila.estado]),
            notaUnica && fila.estado === 'guardada' && chip('Nota única', 'primario'),
            notaUnica && fila.estado === 'modificada' && chip('Reemplaza la nota única', 'aviso')));
        fila.celdaAcciones.replaceChildren(fila.nota
            ? botonIcono('eliminar', `Eliminar la nota de ${fila.alumno.nombreCompleto}`, () => eliminarNota(fila), 'peligro')
            : '');
    }

    function actualizarResumen() {
        const conNota = filas.filter((fila) => fila.nota);
        resumen.replaceChildren(
            chip(materia.carreraNombre, 'primario'),
            chip(`${materia.semestre}° semestre · ${materia.creditos} créditos`),
            chip(materia.docenteNombre ? `Docente: ${materia.docenteNombre}` : 'Sin docente asignado', materia.docenteNombre ? 'neutro' : 'aviso'),
            chip(`${conNota.length} de ${filas.length} con nota`),
            chip(`Promedio: ${formatearNumero(promedio(conNota.map((fila) => fila.nota.nota)))}`));
        const cantidad = pendientes().length;
        const conError = filas.filter((fila) => fila.estado === 'invalida' || fila.estado === 'vaciada').length;
        barraGuardar.hidden = cantidad === 0 && conError === 0;
        contador.textContent = [
            cantidad && `${cantidad === 1 ? '1 nota' : cantidad + ' notas'} sin guardar`,
            conError && `${conError === 1 ? '1 valor no válido' : conError + ' valores no válidos'}`,
        ].filter(Boolean).join(' · ');
        botonGuardar.disabled = cantidad === 0;
    }

    function cargarEntradas(fila) {
        const valores = originales(fila);
        fila.entradas.forEach((entrada, i) => { entrada.value = valores[i]; });
    }

    const cuerpo = el('tbody');
    inscriptos.forEach((inscripto, indice) => {
        const alumno = { id: inscripto.estudianteId, nombreCompleto: inscripto.nombreCompleto, matricula: inscripto.matricula };
        const nota = porEstudiante.get(alumno.id) ?? null;
        const filaAsistencia = asistenciaDe.get(alumno.id);
        const sinDerechoAFinal = filaAsistencia?.condicion === 'NO_HABILITADO';

        const entradas = COMPONENTES_NOTA.map((componente, columna) => {
            const bloqueada = columna === 3 && sinDerechoAFinal && (nota?.examenFinal === null || nota?.examenFinal === undefined);
            return el('input', {
                type: 'number', class: 'planilla__nota planilla__nota--chica', min: 0, max: componente.maximo, step: 1,
                inputmode: 'numeric', disabled: bloqueada,
                title: bloqueada ? `No habilitado por asistencia (${textoPorcentaje(filaAsistencia.porcentaje)})` : `De 0 a ${componente.maximo}`,
                'aria-label': `${componente.titulo} de ${alumno.nombreCompleto}`,
            });
        });
        const fila = {
            alumno, nota, entradas,
            celdaTotal: el('td', { class: 'num planilla__total' }), celdaCalificacion: el('td'),
            celdaEstado: el('td'), celdaAcciones: el('td', { class: 'acciones' }),
        };
        cargarEntradas(fila);

        entradas.forEach((entrada, columna) => {
            entrada.addEventListener('input', () => {
                actualizarFila(fila);
                actualizarResumen();
            });
            // Enter baja al mismo componente del siguiente estudiante (carga rápida por columna)
            entrada.addEventListener('keydown', (evento) => {
                if (evento.key !== 'Enter') return;
                evento.preventDefault();
                const siguiente = filas.slice(indice + 1).find((otra) => !otra.tr.hidden && !otra.entradas[columna].disabled);
                if (siguiente) {
                    siguiente.entradas[columna].focus();
                    siguiente.entradas[columna].select();
                } else if (pendientes().length) {
                    guardar();
                }
            });
        });

        const marcaAsistencia = filaAsistencia && ['EN_RIESGO', 'NO_HABILITADO', 'EXONERADO'].includes(filaAsistencia.condicion)
            && el('span', { title: `Asistencia: ${textoPorcentaje(filaAsistencia.porcentaje)}` }, chipCondicion(filaAsistencia.condicion));
        fila.tr = el('tr', {},
            el('td', {}, el('div', { class: 'celda-estudiante' },
                persona(alumno.nombreCompleto, `Matrícula ${alumno.matricula}`), marcaAsistencia)),
            entradas.map((entrada) => el('td', { class: 'planilla__componente' }, entrada)),
            fila.celdaTotal, fila.celdaCalificacion, fila.celdaEstado, fila.celdaAcciones);
        fila.tr.dataset.busqueda = normalizar(`${alumno.nombreCompleto} ${alumno.matricula} ${inscripto.username}`);
        filas.push(fila);
        actualizarFila(fila);
        cuerpo.append(fila.tr);
    });
    cuerpo.append(el('tr', { class: 'tabla__sin-resultados', hidden: true },
        el('td', { colspan: 9 }, 'No hay resultados para esa búsqueda.')));
    actualizarResumen();

    const componentesDe = (fila) => Object.fromEntries(COMPONENTES_NOTA.map(({ clave }, i) => {
        const texto = leer(fila)[i];
        return [clave, texto === '' ? null : Number(texto)];
    }));

    async function guardar() {
        const cambios = pendientes();
        if (!cambios.length) return;
        botonGuardar.disabled = true;
        botonGuardar.classList.add('boton--ocupado');
        const resultados = await Promise.allSettled(cambios.map((fila) => (fila.nota
            ? api('PATCH', `/api/calificaciones/${fila.nota.id}`, componentesDe(fila))
            : api('POST', '/api/calificaciones', { estudianteId: fila.alumno.id, materiaId: materia.id, ...componentesDe(fila) }))));
        botonGuardar.classList.remove('boton--ocupado');
        const errores = [];
        resultados.forEach((resultado, i) => {
            if (resultado.status === 'fulfilled') {
                cambios[i].nota = resultado.value;
                cargarEntradas(cambios[i]);
                actualizarFila(cambios[i]);
            } else {
                errores.push(`${cambios[i].alumno.nombreCompleto}: ${resultado.reason.message}`);
            }
        });
        actualizarResumen();
        const guardadas = cambios.length - errores.length;
        if (errores.length) avisar(`${guardadas} guardadas; no se pudo guardar ${errores.length}. ${errores[0]}`, 'error');
        else avisar(guardadas === 1 ? 'Nota guardada.' : `${guardadas} notas guardadas.`, 'exito');
    }

    function descartar() {
        for (const fila of filas) {
            cargarEntradas(fila);
            actualizarFila(fila);
        }
        actualizarResumen();
    }

    async function eliminarNota(fila) {
        const aceptar = await confirmar({
            titulo: 'Eliminar nota',
            mensaje: `Se eliminará la nota ${fila.nota.nota} de ${fila.alumno.nombreCompleto} en ${materia.codigo} (con todos sus componentes).`,
            textoBoton: 'Eliminar', peligro: true,
        });
        if (!aceptar) return;
        try {
            await api('DELETE', `/api/calificaciones/${fila.nota.id}`);
            fila.nota = null;
            cargarEntradas(fila);
            actualizarFila(fila);
            actualizarResumen();
            avisar('Nota eliminada.', 'exito');
        } catch (error) {
            avisar(error.message, 'error');
        }
    }

    const tablaPlanilla = inscriptos.length
        ? el('div', { class: 'tabla-contenedor' },
            el('table', { class: 'tabla planilla' },
                el('thead', {}, el('tr', {},
                    el('th', {}, 'Estudiante'),
                    COMPONENTES_NOTA.map((c) => el('th', { class: 'planilla__componente' }, c.titulo, el('span', { class: 'th__max' }, ` /${c.maximo}`))),
                    el('th', { class: 'num' }, 'Total'), el('th', {}, 'Calificación'), el('th', {}, 'Estado'),
                    el('th', { class: 'acciones' }, ''))),
                cuerpo))
        : estadoVacio({
            iconoNombre: 'estudiantes', titulo: `No hay estudiantes inscriptos en ${materia.codigo}`,
            texto: 'Inscriba a los estudiantes que cursan la materia para poder cargarles notas.',
            accion: boton('Inscribir estudiantes', () => ir(`asistencia?materia=${materia.id}&ver=inscriptos`), { variante: 'primario' }),
        });
    const contenedor = el('div', {}, tablaPlanilla);

    return el('div', { class: 'pila' },
        panel({ contenido: el('div', { class: 'planilla__cabecera' }, selector, resumen), clase: 'panel--selector' }),
        inscriptos.length > 0 && barraHerramientas(
            buscador('Buscar estudiante…', () => contenedor),
            el('span', { class: 'herramientas__ayuda' }, icono('info'),
                'Parciales de 15, trabajo práctico de 20 y examen final de 50: se aprueba con 60. Enter baja al siguiente estudiante.'),
            el('span', { class: 'herramientas__empuje' }),
            boton('Asistencia', () => ir(`asistencia?materia=${materia.id}&ver=resumen`), { variante: 'fantasma', iconoNombre: 'asistencia' }),
            botonExcel('Exportar planilla', `/api/exportar/notas?materiaId=${materia.id}`)),
        contenedor,
        barraGuardar);
}

// ======================= Estudiantes =======================

async function vistaEstudiantes(parametros) {
    const carreraId = parametros.get('carrera') ?? '';
    const [carreras, estudiantes] = await Promise.all([
        api('GET', '/api/carreras'),
        api('GET', conParametro('/api/estudiantes', 'carreraId', carreraId)),
    ]);
    if (!esAdmin() && !carreras.length) {
        return estadoVacio({ iconoNombre: 'estudiantes', titulo: 'Todavía no tiene materias a cargo',
            texto: 'Podrá agregar y ver estudiantes de las carreras donde tenga materias asignadas.' });
    }
    ponerAcciones(
        botonExcel('Exportar', conParametro('/api/exportar/estudiantes', 'carreraId', carreraId)),
        boton('Nuevo estudiante', () => formularioEstudiante(carreras), { variante: 'primario', iconoNombre: 'mas' }));

    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Estudiante', celda: (e) => persona(e.nombreCompleto, `Usuario: ${e.username}`) },
            { titulo: 'Matrícula', celda: (e) => e.matricula },
            { titulo: 'Carrera', celda: (e) => chip(e.carreraNombre, 'primario') },
            { titulo: 'Estado', celda: (e) => chip(e.activo ? 'Activo' : 'Inactivo', e.activo ? 'exito' : 'peligro') },
            { titulo: '', clase: 'acciones', celda: (e) => [
                botonIcono('editar', `Editar a ${e.nombreCompleto}`, () => formularioEstudiante(carreras, e)),
                esAdmin() && botonIcono('eliminar', `Eliminar a ${e.nombreCompleto}`, () => eliminarRegistro(
                    `/api/estudiantes/${e.id}`, 'Eliminar estudiante',
                    `Se eliminará a ${e.nombreCompleto} y su usuario. Solo es posible si no tiene notas.`), 'peligro'),
            ] },
        ],
        filas: estudiantes,
        vacio: estadoVacio({ iconoNombre: 'estudiantes', titulo: 'No hay estudiantes',
            texto: carreraId ? 'No hay estudiantes en esta carrera.' : 'Agregue el primero con "Nuevo estudiante".' }),
    }));

    return el('div', { class: 'pila' },
        barraHerramientas(
            buscador('Buscar por nombre, matrícula o usuario…', () => contenedor, busquedas.get('estudiantes'), (v) => busquedas.set('estudiantes', v)),
            selectorFiltro('Carrera', [{ valor: '', texto: 'Todas' }, ...carreras.map((c) => ({ valor: c.id, texto: c.nombre }))],
                carreraId, (valor) => ir(conParametro('estudiantes', 'carrera', valor)))),
        contenedor);
}

function formularioEstudiante(carreras, estudiante) {
    abrirFormulario({
        titulo: estudiante ? `Editar a ${estudiante.nombreCompleto}` : 'Nuevo estudiante',
        descripcion: estudiante ? null : 'Con el usuario y la contraseña el estudiante podrá ingresar a ver sus notas.',
        campos: [
            { nombre: 'nombreCompleto', etiqueta: 'Nombre completo', maxlength: 150, ancho: 'completo' },
            { nombre: 'matricula', etiqueta: 'Matrícula', maxlength: 20, placeholder: '2026-0002' },
            { nombre: 'carreraId', etiqueta: 'Carrera', tipo: 'select',
                opciones: [{ valor: '', texto: 'Elegir carrera…' }, ...carreras.map((c) => ({ valor: c.id, texto: c.nombre }))] },
            { nombre: 'username', etiqueta: 'Usuario', maxlength: 50, bloqueado: Boolean(estudiante),
                ayuda: estudiante ? 'El usuario no se puede cambiar' : null },
            { nombre: 'password', etiqueta: estudiante ? 'Nueva contraseña' : 'Contraseña', tipo: 'password',
                minlength: 6, maxlength: 72, requerido: !estudiante,
                ayuda: estudiante ? 'Dejar vacío para no cambiarla' : 'Mínimo 6 caracteres' },
        ],
        valores: estudiante ?? { carreraId: carreras.length === 1 ? carreras[0].id : '' },
        textoBoton: estudiante ? 'Guardar cambios' : 'Crear estudiante',
        alGuardar: async (datos) => {
            if (estudiante) {
                await api('PUT', `/api/estudiantes/${estudiante.id}`, {
                    matricula: datos.matricula, nombreCompleto: datos.nombreCompleto,
                    carreraId: Number(datos.carreraId), password: datos.password || null,
                });
                avisar('Estudiante actualizado.', 'exito');
            } else {
                await api('POST', '/api/estudiantes', {
                    username: datos.username.trim(), password: datos.password, matricula: datos.matricula,
                    nombreCompleto: datos.nombreCompleto, carreraId: Number(datos.carreraId),
                });
                avisar(`Estudiante creado. Ya puede ingresar con el usuario "${datos.username.trim()}".`, 'exito');
            }
            await recargar();
        },
    });
}

// ======================= Materias =======================

async function vistaMaterias(parametros) {
    const carreraId = esAdmin() ? parametros.get('carrera') ?? '' : '';
    const [materias, carreras, docentes] = await Promise.all([
        api('GET', conParametro('/api/materias', 'carreraId', carreraId)),
        esAdmin() ? api('GET', '/api/carreras') : [],
        esAdmin() ? api('GET', '/api/docentes') : [],
    ]);
    ponerAcciones(
        botonExcel('Exportar', conParametro('/api/exportar/materias', 'carreraId', carreraId)),
        esAdmin() && boton('Nueva materia', () => formularioMateria(carreras, docentes),
            { variante: 'primario', iconoNombre: 'mas' }));

    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Materia', celda: (m) => el('div', { class: 'celda-doble' },
                el('span', {}, m.nombre), el('span', { class: 'sutil' }, m.codigo)) },
            { titulo: 'Carrera', celda: (m) => chip(m.carreraNombre, 'primario') },
            { titulo: 'Semestre', clase: 'num', celda: (m) => `${m.semestre}°` },
            { titulo: 'Créditos', clase: 'num', celda: (m) => m.creditos },
            { titulo: 'Inscriptos', clase: 'num', celda: (m) => m.inscriptos },
            { titulo: 'Asistencia mín.', clase: 'num', celda: (m) => `${m.asistenciaMinima} %` },
            esAdmin() && { titulo: 'Docente', celda: (m) => (m.docenteNombre ? persona(m.docenteNombre, m.docenteUsername) : chip('Sin asignar', 'aviso')) },
            { titulo: '', clase: 'acciones', celda: (m) => (esAdmin()
                ? [
                    botonIcono('editar', `Editar ${m.codigo}`, () => formularioMateria(carreras, docentes, m)),
                    botonIcono('eliminar', `Eliminar ${m.codigo}`, () => eliminarRegistro(`/api/materias/${m.id}`,
                        'Eliminar materia', `Se eliminará ${m.codigo} · ${m.nombre}. Solo es posible si no tiene notas.`), 'peligro'),
                ]
                : [
                    boton('Asistencia', () => ir(`asistencia?materia=${m.id}`), { variante: 'fantasma', iconoNombre: 'asistencia' }),
                    boton('Notas', () => ir(`notas?materia=${m.id}`), { variante: 'fantasma', iconoNombre: 'flecha' }),
                ]) },
        ].filter(Boolean),
        filas: materias,
        vacio: esAdmin()
            ? estadoVacio({ iconoNombre: 'materias', titulo: 'No hay materias', texto: 'Cree la primera con "Nueva materia".' })
            : estadoVacio({ iconoNombre: 'materias', titulo: 'Todavía no tiene materias a cargo', texto: 'Pida al administrador que le asigne sus materias.' }),
    }));

    return el('div', { class: 'pila' },
        barraHerramientas(
            buscador('Buscar materia…', () => contenedor, busquedas.get('materias'), (v) => busquedas.set('materias', v)),
            esAdmin() && selectorFiltro('Carrera', [{ valor: '', texto: 'Todas' }, ...carreras.map((c) => ({ valor: c.id, texto: c.nombre }))],
                carreraId, (valor) => ir(conParametro('materias', 'carrera', valor)))),
        contenedor);
}

function formularioMateria(carreras, docentes, materia) {
    if (!carreras.length) {
        avisar('Primero cree una carrera en la pestaña Carreras.', 'error');
        return;
    }
    abrirFormulario({
        titulo: materia ? `Editar ${materia.codigo}` : 'Nueva materia',
        campos: [
            { nombre: 'codigo', etiqueta: 'Código', maxlength: 20, placeholder: 'INF-301' },
            { nombre: 'nombre', etiqueta: 'Nombre', maxlength: 120 },
            { nombre: 'carreraId', etiqueta: 'Carrera', tipo: 'select', ancho: 'completo',
                opciones: [{ valor: '', texto: 'Elegir carrera…' }, ...carreras.map((c) => ({ valor: c.id, texto: c.nombre }))] },
            { nombre: 'semestre', etiqueta: 'Semestre', tipo: 'number', min: 1, max: 12, placeholder: '1 a 12' },
            { nombre: 'creditos', etiqueta: 'Créditos', tipo: 'number', min: 1 },
            { nombre: 'docenteId', etiqueta: 'Docente a cargo', tipo: 'select', requerido: false, ancho: 'completo',
                opciones: [{ valor: '', texto: 'Sin asignar' }, ...docentes.map((d) => ({ valor: d.id, texto: `${d.nombreCompleto} (${d.username})` }))],
                ayuda: 'Solo el docente a cargo pasa lista y carga las notas de esta materia' },
            { nombre: 'asistenciaMinima', etiqueta: 'Asistencia mínima (%)', tipo: 'number', min: 0, max: 100, requerido: false,
                placeholder: 'La de la carrera', ayuda: 'Vacío = el mínimo de la carrera (70 % por defecto)' },
            { nombre: 'clasesPrevistas', etiqueta: 'Clases previstas', tipo: 'number', min: 1, max: 300, requerido: false,
                placeholder: 'Ej.: 32', ayuda: 'Para calcular cuántas faltas le quedan a cada estudiante' },
        ],
        valores: materia
            ? { ...materia, asistenciaMinima: materia.asistenciaMinimaPropia }
            : { carreraId: carreras.length === 1 ? carreras[0].id : '' },
        textoBoton: materia ? 'Guardar cambios' : 'Crear materia',
        alGuardar: async (datos) => {
            const cuerpo = {
                codigo: datos.codigo, nombre: datos.nombre, creditos: Number(datos.creditos),
                semestre: Number(datos.semestre), carreraId: Number(datos.carreraId),
                docenteId: datos.docenteId ? Number(datos.docenteId) : null,
                asistenciaMinima: datos.asistenciaMinima === '' ? null : Number(datos.asistenciaMinima),
                clasesPrevistas: datos.clasesPrevistas === '' ? null : Number(datos.clasesPrevistas),
            };
            await api(materia ? 'PUT' : 'POST', materia ? `/api/materias/${materia.id}` : '/api/materias', cuerpo);
            avisar(materia ? 'Materia actualizada.' : 'Materia creada.', 'exito');
            await recargar();
        },
    });
}

// ======================= Docentes =======================

async function vistaDocentes() {
    const docentes = await api('GET', '/api/docentes');
    ponerAcciones(
        botonExcel('Exportar', '/api/exportar/docentes'),
        boton('Nuevo docente', () => formularioDocente(), { variante: 'primario', iconoNombre: 'mas' }));

    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Docente', celda: (d) => persona(d.nombreCompleto, `Usuario: ${d.username}`) },
            { titulo: 'Materias a cargo', celda: (d) => (d.materias.length
                ? el('div', { class: 'chips' }, d.materias.map((codigo) => chip(codigo, 'primario')))
                : chip('Ninguna', 'aviso')) },
            { titulo: 'Estado', celda: (d) => chip(d.activo ? 'Activo' : 'Inactivo', d.activo ? 'exito' : 'peligro') },
            { titulo: '', clase: 'acciones', celda: (d) => [
                botonIcono('editar', `Editar a ${d.nombreCompleto}`, () => formularioDocente(d)),
                botonIcono('eliminar', `Eliminar a ${d.nombreCompleto}`, () => eliminarRegistro(`/api/docentes/${d.id}`,
                    'Eliminar docente', `Se eliminará a ${d.nombreCompleto} y su usuario. Solo es posible si no tiene materias a cargo.`), 'peligro'),
            ] },
        ],
        filas: docentes,
        vacio: estadoVacio({ iconoNombre: 'docentes', titulo: 'No hay docentes', texto: 'Cree el primero con "Nuevo docente" y después asígnele materias.' }),
    }));

    return el('div', { class: 'pila' },
        barraHerramientas(buscador('Buscar docente…', () => contenedor, busquedas.get('docentes'), (v) => busquedas.set('docentes', v))),
        contenedor);
}

function formularioDocente(docente) {
    abrirFormulario({
        titulo: docente ? `Editar a ${docente.nombreCompleto}` : 'Nuevo docente',
        descripcion: docente ? null : 'Con este usuario el profesor ingresa al sistema. Las materias se le asignan en la pestaña Materias.',
        campos: [
            { nombre: 'nombreCompleto', etiqueta: 'Nombre completo', maxlength: 150, ancho: 'completo', placeholder: 'Lic. María Benítez' },
            { nombre: 'username', etiqueta: 'Usuario', maxlength: 50, bloqueado: Boolean(docente) },
            { nombre: 'password', etiqueta: docente ? 'Nueva contraseña' : 'Contraseña', tipo: 'password', minlength: 6, maxlength: 72,
                requerido: !docente, ayuda: docente ? 'Dejar vacío para no cambiarla' : 'Mínimo 6 caracteres' },
        ],
        valores: docente ?? {},
        textoBoton: docente ? 'Guardar cambios' : 'Crear docente',
        alGuardar: async (datos) => {
            if (docente) {
                await api('PUT', `/api/docentes/${docente.id}`, { nombreCompleto: datos.nombreCompleto, password: datos.password || null });
                avisar('Docente actualizado.', 'exito');
            } else {
                await api('POST', '/api/docentes', { username: datos.username.trim(), password: datos.password, nombreCompleto: datos.nombreCompleto });
                avisar(`Docente creado. Ya puede ingresar con el usuario "${datos.username.trim()}".`, 'exito');
            }
            await recargar();
        },
    });
}

// ======================= Carreras =======================

async function vistaCarreras() {
    const carreras = await api('GET', '/api/carreras');
    ponerAcciones(
        botonExcel('Exportar', '/api/exportar/carreras'),
        boton('Nueva carrera', () => formularioCarrera(), { variante: 'primario', iconoNombre: 'mas' }));

    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Carrera', celda: (c) => el('div', { class: 'celda-doble' }, el('span', {}, c.nombre), el('span', { class: 'sutil' }, c.codigo)) },
            { titulo: 'Semestres', clase: 'num', celda: (c) => c.semestres },
            { titulo: 'Asistencia mín.', clase: 'num', celda: (c) => `${c.asistenciaMinima} %` },
            { titulo: 'Materias', clase: 'num', celda: (c) => c.materias },
            { titulo: 'Estudiantes', clase: 'num', celda: (c) => c.estudiantes },
            { titulo: '', clase: 'acciones', celda: (c) => [
                botonExcel('Plantilla', `/api/carga-masiva/plantilla?carreraId=${c.id}`, 'fantasma'),
                boton('Ver estudiantes', () => ir(`estudiantes?carrera=${c.id}`), { variante: 'fantasma' }),
                botonIcono('editar', `Editar ${c.nombre}`, () => formularioCarrera(c)),
                botonIcono('eliminar', `Eliminar ${c.nombre}`, () => eliminarRegistro(`/api/carreras/${c.id}`,
                    'Eliminar carrera', `Se eliminará ${c.nombre}. Solo es posible si no tiene materias ni estudiantes.`), 'peligro'),
            ] },
        ],
        filas: carreras,
        vacio: estadoVacio({ iconoNombre: 'carreras', titulo: 'No hay carreras', texto: 'Cree la primera con "Nueva carrera".' }),
    }));

    return el('div', { class: 'pila' },
        barraHerramientas(buscador('Buscar carrera…', () => contenedor, busquedas.get('carreras'), (v) => busquedas.set('carreras', v))),
        contenedor);
}

function formularioCarrera(carrera) {
    abrirFormulario({
        titulo: carrera ? `Editar ${carrera.nombre}` : 'Nueva carrera',
        campos: [
            { nombre: 'codigo', etiqueta: 'Código', maxlength: 20, placeholder: 'DER' },
            { nombre: 'semestres', etiqueta: 'Duración (semestres)', tipo: 'number', min: 1, max: 12, placeholder: '1 a 12' },
            { nombre: 'nombre', etiqueta: 'Nombre', maxlength: 120, placeholder: 'Derecho', ancho: 'completo' },
            { nombre: 'asistenciaMinima', etiqueta: 'Asistencia mínima (%)', tipo: 'number', min: 0, max: 100, ancho: 'completo',
                ayuda: 'Porcentaje de asistencia para rendir el examen final (reglamento UPE: 70 %). Cada materia puede tener el suyo.' },
        ],
        valores: carrera ?? { semestres: 10, asistenciaMinima: 70 },
        textoBoton: carrera ? 'Guardar cambios' : 'Crear carrera',
        alGuardar: async (datos) => {
            await api(carrera ? 'PUT' : 'POST', carrera ? `/api/carreras/${carrera.id}` : '/api/carreras',
                { codigo: datos.codigo, nombre: datos.nombre, semestres: Number(datos.semestres),
                    asistenciaMinima: Number(datos.asistenciaMinima) });
            avisar(carrera ? 'Carrera actualizada.' : 'Carrera creada.', 'exito');
            await recargar();
        },
    });
}

// ======================= Usuarios =======================

async function vistaUsuarios() {
    const usuarios = await api('GET', '/api/usuarios');
    ponerAcciones(boton('Nuevo administrador', () => formularioAdministrador(), { variante: 'primario', iconoNombre: 'mas' }));
    const tipoRol = { ADMIN: 'primario', DOCENTE: 'neutro', ESTUDIANTE: 'neutro' };

    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Usuario', celda: (u) => persona(u.username, u.username === estado.sesion.username ? 'Usted' : null) },
            { titulo: 'Rol', celda: (u) => chip(NOMBRES_ROL[u.rol], tipoRol[u.rol]) },
            { titulo: 'Estado', celda: (u) => chip(u.activo ? 'Activo' : 'Inactivo', u.activo ? 'exito' : 'peligro') },
            { titulo: 'Verificación en 2 pasos', celda: (u) => (u.dosFactores ? chip('Activa', 'exito') : el('span', { class: 'sutil' }, 'No')) },
            { titulo: '', clase: 'acciones', celda: (u) => [
                u.dosFactores && u.username !== estado.sesion.username
                    && boton('Quitar 2FA', () => quitarDosFactores(u), { variante: 'fantasma' }),
                botonIcono('llave', `Cambiar la contraseña de ${u.username}`, () => formularioContrasenaDe(u)),
                u.username !== estado.sesion.username && (u.activo
                    ? boton('Desactivar', () => cambiarActivo(u, false), { variante: 'fantasma' })
                    : boton('Activar', () => cambiarActivo(u, true), { variante: 'fantasma' })),
            ] },
        ],
        filas: usuarios,
        vacio: estadoVacio({ titulo: 'No hay usuarios' }),
    }));

    return el('div', { class: 'pila' },
        barraHerramientas(buscador('Buscar usuario…', () => contenedor, busquedas.get('usuarios'), (v) => busquedas.set('usuarios', v))),
        contenedor);
}

function formularioAdministrador() {
    abrirFormulario({
        titulo: 'Nuevo administrador',
        descripcion: 'Tendrá acceso completo al sistema. Los docentes y estudiantes se crean en sus pestañas.',
        campos: [
            { nombre: 'username', etiqueta: 'Usuario', maxlength: 50, ancho: 'completo' },
            { nombre: 'password', etiqueta: 'Contraseña', tipo: 'password', minlength: 6, maxlength: 72, ancho: 'completo', ayuda: 'Mínimo 6 caracteres' },
        ],
        textoBoton: 'Crear administrador',
        alGuardar: async (datos) => {
            await api('POST', '/api/usuarios', { username: datos.username.trim(), password: datos.password });
            avisar(`Administrador "${datos.username.trim()}" creado.`, 'exito');
            await recargar();
        },
    });
}

function formularioContrasenaDe(usuario) {
    abrirFormulario({
        titulo: `Contraseña de ${usuario.username}`,
        campos: [
            { nombre: 'nueva', etiqueta: 'Nueva contraseña', tipo: 'password', minlength: 6, maxlength: 72, ancho: 'completo' },
            { nombre: 'repetir', etiqueta: 'Repetir contraseña', tipo: 'password', minlength: 6, maxlength: 72, ancho: 'completo' },
        ],
        textoBoton: 'Cambiar contraseña',
        alGuardar: async (datos) => {
            if (datos.nueva !== datos.repetir) throw new Error('Las contraseñas no coinciden.');
            await api('PATCH', `/api/usuarios/${usuario.id}`, { password: datos.nueva });
            avisar('Contraseña cambiada.', 'exito');
        },
    });
}

async function cambiarActivo(usuario, activo) {
    const aceptar = activo || await confirmar({
        titulo: 'Desactivar usuario',
        mensaje: `${usuario.username} ya no podrá ingresar al sistema hasta que se lo vuelva a activar.`,
        textoBoton: 'Desactivar', peligro: true,
    });
    if (!aceptar) return;
    try {
        await api('PATCH', `/api/usuarios/${usuario.id}`, { activo });
        avisar(activo ? 'Usuario activado.' : 'Usuario desactivado.', 'exito');
        await recargar();
    } catch (error) {
        avisar(error.message, 'error');
    }
}

async function quitarDosFactores(usuario) {
    const aceptar = await confirmar({
        titulo: 'Quitar la verificación en dos pasos',
        mensaje: `${usuario.username} podrá ingresar solo con su contraseña (por ejemplo, si perdió el celular). `
            + 'Después puede volver a activarla desde sus Ajustes.',
        textoBoton: 'Quitar 2FA', peligro: true,
    });
    if (!aceptar) return;
    try {
        await api('PATCH', `/api/usuarios/${usuario.id}`, { quitarDosFactores: true });
        avisar(`Se quitó la verificación en dos pasos de ${usuario.username}.`, 'exito');
        await recargar();
    } catch (error) {
        avisar(error.message, 'error');
    }
}

async function eliminarRegistro(ruta, titulo, mensaje) {
    if (!(await confirmar({ titulo, mensaje, textoBoton: 'Eliminar', peligro: true }))) return;
    try {
        await api('DELETE', ruta);
        avisar('Eliminado correctamente.', 'exito');
        await recargar();
    } catch (error) {
        avisar(error.message, 'error');
    }
}

// ======================= Mis notas (estudiante) =======================

async function vistaMisNotas() {
    const notas = await api('GET', '/api/estudiantes/me/calificaciones');
    const valores = notas.map((nota) => nota.nota);
    if (notas.length) ponerAcciones(botonExcel('Descargar en Excel', '/api/exportar/mis-notas'));
    const componente = (n, clave) => (n.desglosada ? (n[clave] ?? '–') : '');

    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Materia', celda: (n) => el('div', { class: 'celda-doble' }, el('span', {}, n.nombreMateria),
                el('span', { class: 'sutil' }, `${n.codigoMateria} · ${n.semestre}° semestre · ${n.docenteNombre ?? 'sin docente'}`)) },
            ...COMPONENTES_NOTA.map(({ clave, titulo, maximo }) => ({
                titulo: `${titulo} /${maximo}`, clase: 'num', celda: (n) => componente(n, clave),
            })),
            { titulo: 'Total', clase: 'columna-avance', celda: (n) => el('div', { class: 'medidor' },
                el('span', { class: 'medidor__valor' }, n.nota),
                el('div', { class: 'medidor__pista' }, el('div', { class: 'medidor__relleno', style: `width: ${n.nota}%` }))) },
            { titulo: 'Calificación', celda: (n) => chipCalificacion(n.calificacion) },
        ],
        filas: notas,
        vacio: estadoVacio({ iconoNombre: 'notas', titulo: 'Todavía no tiene notas', texto: 'Cuando sus docentes carguen notas aparecerán aquí.' }),
    }));

    return el('div', { class: 'pila' },
        el('div', { class: 'tarjetas' },
            tarjetaDato({ iconoNombre: 'grafico', titulo: 'Promedio', valor: formatearNumero(promedio(valores)) }),
            tarjetaDato({ iconoNombre: 'materias', titulo: 'Materias con nota', valor: notas.length }),
            tarjetaDato({ iconoNombre: 'check', titulo: 'Aprobadas', valor: notas.filter((n) => n.calificacion >= 2).length,
                detalle: `${notas.filter((n) => n.calificacion === null).length} en curso` }),
            tarjetaDato({ iconoNombre: 'estrella', titulo: 'Mejor nota', valor: valores.length ? Math.max(...valores) : '–' })),
        el('p', { class: 'herramientas__ayuda' }, icono('info'),
            'Parciales de 15, trabajo práctico de 20 y examen final de 50 (total 100). Se aprueba con 60: 60-69 = 2, 70-79 = 3, 80-90 = 4, 91-100 = 5.'),
        notas.length > 6 && barraHerramientas(buscador('Buscar materia…', () => contenedor)),
        contenedor);
}

// ======================= Mi asistencia (estudiante) =======================

async function vistaMiAsistencia() {
    const materias = await api('GET', '/api/estudiantes/me/asistencia');
    if (!materias.length) {
        return estadoVacio({ iconoNombre: 'asistencia', titulo: 'Todavía no está inscripto en materias',
            texto: 'Cuando sus docentes lo inscriban y pasen lista, verá aquí su asistencia.' });
    }

    const conPorcentaje = materias.map((m) => m.resumen.porcentaje).filter((valor) => valor !== null);
    const enRiesgo = materias.filter((m) => m.resumen.condicion === 'EN_RIESGO');
    const perdidas = materias.filter((m) => m.resumen.condicion === 'NO_HABILITADO');
    const nombre = (m) => el('strong', {}, `${m.codigoMateria} · ${m.nombreMateria}: `);
    const avisos = [
        ...perdidas.map((m) => el('div', { class: 'resultado resultado--error' }, icono('alerta'), el('span', {}, nombre(m),
            `ya no alcanza el ${m.asistenciaMinima} % de asistencia necesario para rendir el examen final. `
            + 'Consulte con su docente o con la coordinación.'))),
        ...enRiesgo.map((m) => el('div', { class: 'resultado resultado--aviso' }, icono('alerta'), el('span', {}, nombre(m),
            m.resumen.faltasRestantes !== null
                ? `le ${m.resumen.faltasRestantes === 1 ? 'queda 1 falta' : `quedan ${m.resumen.faltasRestantes} faltas`} antes de perder el derecho al examen final.`
                : `su asistencia (${textoPorcentaje(m.resumen.porcentaje)}) está cerca del mínimo de ${m.asistenciaMinima} %.`))),
    ];

    return el('div', { class: 'pila' },
        el('div', { class: 'tarjetas' },
            tarjetaDato({ iconoNombre: 'asistencia', titulo: 'Asistencia promedio', valor: textoPorcentaje(promedio(conPorcentaje)) }),
            tarjetaDato({ iconoNombre: 'materias', titulo: 'Materias', valor: materias.length }),
            tarjetaDato({ iconoNombre: 'alerta', titulo: 'En riesgo', valor: enRiesgo.length + perdidas.length,
                detalle: perdidas.length ? `${perdidas.length} sin derecho al final` : 'Ninguna sin derecho al final' }),
            tarjetaDato({ iconoNombre: 'info', titulo: 'Faltas', valor: materias.reduce((suma, m) => suma + m.resumen.ausenciasComputadas, 0),
                detalle: '3 llegadas tarde = 1 falta' })),
        avisos,
        el('div', { class: 'tarjetas-materia' }, materias.map(tarjetaMiAsistencia)));
}

function tarjetaMiAsistencia(m) {
    const r = m.resumen;
    const conteo = [
        [r.presentes, 'presentes', 'exito'], [r.tardes, 'tarde', 'aviso'],
        [r.ausentes, 'ausentes', 'peligro'], [r.justificados, 'justificadas', 'primario'],
    ].filter(([cantidad]) => cantidad > 0);
    const datos = [
        `Mínimo ${m.asistenciaMinima} %`,
        `${m.clasesDictadas} clases dictadas${m.clasesPrevistas ? ` de ${m.clasesPrevistas}` : ''}`,
        r.faltasRestantes !== null && r.condicion !== 'EXONERADO'
            && (r.faltasRestantes === 1 ? 'le queda 1 falta' : `le quedan ${r.faltasRestantes} faltas`),
    ].filter(Boolean).join(' · ');

    return panel({
        clase: 'panel--materia',
        titulo: m.nombreMateria,
        descripcion: `${m.codigoMateria} · ${m.semestre}° semestre${m.docenteNombre ? ' · ' + m.docenteNombre : ''}`,
        acciones: chipCondicion(r.condicion),
        contenido: el('div', { class: 'pila pila--chica' },
            medidorAsistencia(r.porcentaje, m.asistenciaMinima),
            conteo.length > 0 && el('div', { class: 'chips' }, conteo.map(([cantidad, texto, tipo]) => chip(`${cantidad} ${texto}`, tipo))),
            el('span', { class: 'sutil' }, datos),
            r.motivoExoneracion && el('span', { class: 'sutil' }, `Exonerado de asistencia: ${r.motivoExoneracion}`),
            m.clases.length > 0 && el('details', { class: 'detalles' },
                el('summary', {}, `Ver ${m.clases.length === 1 ? 'la clase' : `las ${m.clases.length} clases`}`),
                tabla({
                    columnas: [
                        { titulo: 'Fecha', celda: (c) => formatearDia(c.fecha) },
                        { titulo: 'Tema', celda: (c) => c.tema ?? '—' },
                        { titulo: 'Asistencia', celda: (c) => chipEstadoAsistencia(c.estado) },
                        { titulo: 'Observación', celda: (c) => el('span', { class: 'sutil' }, c.observacion ?? '') },
                    ],
                    filas: m.clases,
                }))),
    });
}

// ======================= Asistencia (docente y administrador) =======================

const PESTANAS_ASISTENCIA = [
    ['clases', 'Clases', 'asistencia'],
    ['resumen', 'Asistencia y habilitados', 'grafico'],
    ['inscriptos', 'Inscriptos', 'estudiantes'],
];

async function vistaAsistencia(parametros) {
    const materias = await api('GET', '/api/materias');
    if (!materias.length) {
        return esAdmin()
            ? estadoVacio({ iconoNombre: 'materias', titulo: 'Todavía no hay materias', texto: 'Cree las carreras y materias para poder registrar asistencia.',
                accion: boton('Ir a Materias', () => ir('materias'), { variante: 'primario' }) })
            : estadoVacio({ iconoNombre: 'asistencia', titulo: 'Todavía no tiene materias a cargo',
                texto: 'Pida al administrador que le asigne sus materias para poder pasar lista.' });
    }

    const materia = materias.find((m) => String(m.id) === parametros.get('materia'));
    const selector = selectorMateria(materias, materia?.id, 'asistencia');
    if (!materia) {
        return el('div', { class: 'pila' },
            panel({ contenido: selector, clase: 'panel--selector' }),
            estadoVacio({ iconoNombre: 'asistencia', titulo: 'Elija una materia',
                texto: 'Podrá pasar lista, ver el porcentaje de cada estudiante y quién está habilitado para el examen final.' }));
    }
    if (parametros.get('clase')) return pasarLista(materia, parametros.get('clase'), selector);

    const ver = PESTANAS_ASISTENCIA.some(([id]) => id === parametros.get('ver')) ? parametros.get('ver') : 'clases';
    const base = `asistencia?materia=${materia.id}`;
    const pestanas = el('div', { class: 'segmentado pestanas', role: 'tablist' },
        PESTANAS_ASISTENCIA.map(([id, texto, nombreIcono]) => el('button', {
            type: 'button', class: 'segmento', role: 'tab', 'aria-selected': String(id === ver), 'aria-pressed': String(id === ver),
            onclick: () => ir(id === 'clases' ? base : `${base}&ver=${id}`),
        }, icono(nombreIcono), id === 'inscriptos' ? `${texto} (${materia.inscriptos})` : texto)));

    const cabecera = panel({
        clase: 'panel--selector',
        contenido: el('div', { class: 'planilla__cabecera' }, selector, el('div', { class: 'resumen-materia' },
            chip(materia.carreraNombre, 'primario'),
            chip(`${materia.semestre}° semestre`),
            chip(`Mínimo ${materia.asistenciaMinima} %`),
            chip(materia.clasesPrevistas ? `${materia.clasesPrevistas} clases previstas` : 'Sin clases previstas',
                materia.clasesPrevistas ? 'neutro' : 'aviso'),
            chip(materia.docenteNombre ? `Docente: ${materia.docenteNombre}` : 'Sin docente asignado', materia.docenteNombre ? 'neutro' : 'aviso'))),
    });

    let contenido;
    if (ver === 'resumen') contenido = await pestanaResumenAsistencia(materia);
    else if (ver === 'inscriptos') contenido = await pestanaInscriptos(materia);
    else contenido = await pestanaClases(materia);
    return el('div', { class: 'pila' }, cabecera, pestanas, contenido);
}

async function pestanaClases(materia) {
    const base = `asistencia?materia=${materia.id}`;
    if (!materia.inscriptos) {
        return estadoVacio({ iconoNombre: 'estudiantes', titulo: 'Todavía no hay estudiantes inscriptos',
            texto: 'Inscriba a los estudiantes que cursan la materia para poder pasar lista.',
            accion: boton('Inscribir estudiantes', () => ir(`${base}&ver=inscriptos`), { variante: 'primario', iconoNombre: 'mas' }) });
    }
    const clases = await api('GET', `/api/asistencia/clases?materiaId=${materia.id}`);
    const deHoy = clases.find((clase) => clase.fecha === hoyIso());
    ponerAcciones(
        botonExcel('Planilla de asistencia', `/api/exportar/asistencia?materiaId=${materia.id}`),
        boton(deHoy ? 'Abrir la lista de hoy' : 'Pasar lista de hoy', () => ir(`${base}&clase=${deHoy ? deHoy.id : 'nueva'}`),
            { variante: 'primario', iconoNombre: deHoy ? 'editar' : 'mas' }));

    const registrados = (c) => c.presentes + c.tardes + c.ausentes + c.justificados;
    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Fecha', celda: (c) => el('div', { class: 'celda-doble' },
                el('span', {}, formatearDia(c.fecha)), el('span', { class: 'sutil' }, c.tema ?? 'Sin tema')) },
            { titulo: 'Presentes', clase: 'num', celda: (c) => c.presentes },
            { titulo: 'Tarde', clase: 'num', celda: (c) => c.tardes },
            { titulo: 'Ausentes', clase: 'num', celda: (c) => c.ausentes },
            { titulo: 'Justificadas', clase: 'num', celda: (c) => c.justificados },
            { titulo: 'Asistieron', clase: 'columna-avance', celda: (c) => barraAvance(c.presentes + c.tardes, registrados(c)) },
            { titulo: '', clase: 'acciones', celda: (c) => [
                boton('Abrir', () => ir(`${base}&clase=${c.id}`), { variante: 'fantasma', iconoNombre: 'flecha' }),
                botonIcono('eliminar', `Eliminar la clase del ${formatearDia(c.fecha, false)}`, () => eliminarRegistro(
                    `/api/asistencia/clases/${c.id}`, 'Eliminar clase',
                    `Se eliminará la clase del ${formatearDia(c.fecha)} con la asistencia de ${registrados(c)} estudiantes.`), 'peligro'),
            ] },
        ],
        filas: clases,
        vacio: estadoVacio({ iconoNombre: 'asistencia', titulo: 'Todavía no hay clases registradas',
            texto: 'Use "Pasar lista de hoy" para registrar la primera. La fecha se puede cambiar si es de otro día.' }),
    }));
    return el('div', { class: 'pila' }, clases.length > 8 && barraHerramientas(buscador('Buscar por fecha o tema…', () => contenedor)), contenedor);
}

/** Lista de una clase: cada inscripto como Presente, Tarde, Ausente o Justificada. */
async function pasarLista(materia, claseId, selector) {
    const base = `asistencia?materia=${materia.id}`;
    const nueva = claseId === 'nueva';
    let clase = null;
    let registros;
    if (nueva) {
        const inscriptos = await api('GET', `/api/inscripciones?materiaId=${materia.id}`);
        registros = inscriptos.map((i) => ({
            estudianteId: i.estudianteId, matricula: i.matricula, nombreEstudiante: i.nombreCompleto, estado: 'PRESENTE', observacion: null,
        }));
    } else {
        const detalle = await api('GET', `/api/asistencia/clases/${claseId}`);
        clase = detalle.clase;
        registros = detalle.registros;
    }
    if (!registros.length) {
        return estadoVacio({ iconoNombre: 'estudiantes', titulo: 'No hay estudiantes inscriptos',
            accion: boton('Inscribir estudiantes', () => ir(`${base}&ver=inscriptos`), { variante: 'primario' }) });
    }

    let sucio = false;
    cambiosSinGuardar = () => (sucio ? 1 : 0);
    const conteo = el('div', { class: 'resumen-materia resumen-materia--derecha' });
    const botonGuardar = boton(nueva ? 'Guardar lista' : 'Guardar cambios', () => guardar(), { variante: 'primario', iconoNombre: 'guardar' });
    const textoBarra = el('span', {});
    const barra = el('div', { class: 'barra-guardar' },
        el('div', { class: 'barra-guardar__info' }, icono('info'), textoBarra),
        el('div', { class: 'barra-guardar__acciones' }, boton('Cancelar', () => ir(base), { iconoNombre: 'deshacer' }), botonGuardar));

    const marcar = () => {
        sucio = true;
        actualizar();
    };
    const fecha = el('input', { type: 'date', value: clase?.fecha ?? hoyIso(), max: hoyIso(), required: true, oninput: marcar });
    const tema = el('input', { type: 'text', maxlength: 200, placeholder: 'Tema de la clase (opcional)', value: clase?.tema ?? '', oninput: marcar });

    const filas = registros.map((registro) => {
        const fila = { ...registro, botones: [] };
        fila.entradaObservacion = el('input', {
            type: 'text', class: 'entrada-observacion', maxlength: 200, placeholder: 'Observación (opcional)',
            value: registro.observacion ?? '', 'aria-label': `Observación de ${registro.nombreEstudiante}`, oninput: marcar,
        });
        const grupo = el('div', { class: 'segmentado segmentado--asistencia', role: 'group', 'aria-label': `Asistencia de ${registro.nombreEstudiante}` },
            ESTADOS_ASISTENCIA.map((estado) => {
                const botonEstado = el('button', {
                    type: 'button', class: 'segmento', 'data-estado': estado.valor, title: `${estado.texto} (tecla ${estado.letra})`,
                    onclick: () => elegir(fila, estado.valor),
                }, el('span', { class: 'segmento__letra' }, estado.letra), el('span', { class: 'segmento__texto' }, estado.texto));
                fila.botones.push(botonEstado);
                return botonEstado;
            }));
        fila.tr = el('tr', {},
            el('td', {}, persona(registro.nombreEstudiante, `Matrícula ${registro.matricula}`)),
            el('td', {}, grupo),
            el('td', { class: 'columna-observacion' }, fila.entradaObservacion));
        fila.tr.dataset.busqueda = normalizar(`${registro.nombreEstudiante} ${registro.matricula}`);
        // Teclas P, T, A y J sobre los botones de la fila
        fila.tr.addEventListener('keydown', (evento) => {
            if (evento.target.tagName === 'INPUT' || evento.ctrlKey || evento.metaKey || evento.altKey) return;
            const estado = ESTADOS_ASISTENCIA.find((e) => e.letra === evento.key.toUpperCase());
            if (!estado) return;
            evento.preventDefault();
            elegir(fila, estado.valor);
            const siguiente = filas[filas.indexOf(fila) + 1];
            siguiente?.botones.find((b) => b.dataset.estado === estado.valor)?.focus();
        });
        return fila;
    });

    function pintar(fila) {
        fila.botones.forEach((b) => b.setAttribute('aria-pressed', String(b.dataset.estado === fila.estado)));
        fila.tr.dataset.estado = fila.estado ?? '';
    }
    function elegir(fila, estado) {
        fila.estado = estado;
        pintar(fila);
        marcar();
    }
    function actualizar() {
        const cantidad = (valor) => filas.filter((f) => f.estado === valor).length;
        const sinMarcar = filas.filter((f) => !f.estado).length;
        conteo.replaceChildren(...[
            ...ESTADOS_ASISTENCIA.map((e) => chip(`${cantidad(e.valor)} ${e.plural}`, e.tipo)),
            sinMarcar > 0 && chip(`${sinMarcar} sin marcar`),
        ].filter(Boolean));
        textoBarra.textContent = nueva
            ? `Lista nueva de ${filas.length} estudiantes: todos empiezan como presentes.`
            : (sucio ? 'Hay cambios sin guardar en esta lista.' : `Clase del ${formatearDia(clase.fecha)}.`);
        botonGuardar.disabled = !nueva && !sucio;
    }
    filas.forEach(pintar);
    actualizar();

    async function guardar() {
        if (!fecha.value) {
            avisar('Indique la fecha de la clase.', 'error');
            fecha.focus();
            return;
        }
        botonGuardar.disabled = true;
        botonGuardar.classList.add('boton--ocupado');
        const cuerpo = {
            materiaId: materia.id, fecha: fecha.value, tema: tema.value.trim() || null,
            registros: filas.map((f) => ({ estudianteId: f.estudianteId, estado: f.estado ?? null, observacion: f.entradaObservacion.value.trim() || null })),
        };
        try {
            if (nueva) await api('POST', '/api/asistencia/clases', cuerpo);
            else await api('PUT', `/api/asistencia/clases/${clase.id}`, cuerpo);
            sucio = false;
            cambiosSinGuardar = () => 0;
            avisar(`Lista del ${formatearDia(fecha.value)} guardada.`, 'exito');
            ir(base);
        } catch (error) {
            avisar(error.message, 'error');
            botonGuardar.disabled = false;
        } finally {
            botonGuardar.classList.remove('boton--ocupado');
        }
    }

    const contenedor = el('div', { class: 'tabla-contenedor' },
        el('table', { class: 'tabla lista-asistencia' },
            el('thead', {}, el('tr', {}, el('th', {}, 'Estudiante'), el('th', {}, 'Asistencia'), el('th', {}, 'Observación'))),
            el('tbody', {}, filas.map((f) => f.tr),
                el('tr', { class: 'tabla__sin-resultados', hidden: true }, el('td', { colspan: 3 }, 'No hay resultados para esa búsqueda.')))));

    const todosPresentes = boton('Todos presentes', () => {
        filas.filter((f) => !f.tr.hidden).forEach((f) => {
            f.estado = 'PRESENTE';
            pintar(f);
        });
        marcar();
    }, { iconoNombre: 'check' });

    return el('div', { class: 'pila' },
        panel({
            clase: 'panel--selector',
            contenido: el('div', { class: 'planilla__cabecera' }, selector,
                el('label', { class: 'campo campo--fecha' }, el('span', { class: 'campo__etiqueta' }, 'Fecha de la clase'), fecha),
                el('label', { class: 'campo campo--tema' }, el('span', { class: 'campo__etiqueta' }, 'Tema'), tema)),
        }),
        barraHerramientas(
            boton('Volver a las clases', () => ir(base), { variante: 'fantasma', iconoNombre: 'atras' }),
            buscador('Buscar estudiante…', () => contenedor),
            todosPresentes,
            el('span', { class: 'herramientas__empuje' }),
            conteo),
        el('p', { class: 'herramientas__ayuda' }, icono('info'),
            'Toque P, T, A o J en cada estudiante (o use esas teclas). 3 llegadas tardías = 1 ausencia; la justificada queda registrada pero cuenta como ausencia.'),
        contenedor,
        barra);
}

async function pestanaResumenAsistencia(materia) {
    const resumen = await api('GET', `/api/asistencia/resumen?materiaId=${materia.id}`);
    ponerAcciones(
        botonExcel('Planilla de asistencia', `/api/exportar/asistencia?materiaId=${materia.id}`),
        botonExcel('Lista de habilitados', `/api/exportar/habilitados?materiaId=${materia.id}`, 'primario'));

    const filas = resumen.estudiantes;
    const cuantos = (condicion) => filas.filter((f) => f.condicion === condicion).length;
    const habilitados = filas.length - cuantos('NO_HABILITADO');
    const porcentajes = filas.map((f) => f.porcentaje).filter((valor) => valor !== null);
    const previstas = resumen.clasesPrevistas !== null;

    const reglas = el('div', { class: 'resultado' }, icono('info'), el('span', {},
        `Para rendir el examen final hace falta el ${resumen.asistenciaMinima} % de asistencia. `
        + '3 llegadas tardías = 1 ausencia y la ausencia justificada sigue contando. '
        + (previstas ? `Las faltas restantes se calculan sobre ${resumen.clasesPrevistas} clases previstas.`
            : (esAdmin() ? 'Cargue las clases previstas de la materia (en Materias) para ver cuántas faltas le quedan a cada estudiante.'
                : 'Si el administrador carga las clases previstas, verá también cuántas faltas le quedan a cada estudiante.'))));

    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Estudiante', celda: (f) => persona(f.nombreEstudiante, `Matrícula ${f.matricula}`) },
            { titulo: 'Pres.', clase: 'num', celda: (f) => f.presentes },
            { titulo: 'Tarde', clase: 'num', celda: (f) => f.tardes },
            { titulo: 'Aus.', clase: 'num', celda: (f) => f.ausentes },
            { titulo: 'Just.', clase: 'num', celda: (f) => f.justificados },
            { titulo: 'Faltas', clase: 'num', celda: (f) => el('span', { title: 'Ausencias + justificadas + 1 por cada 3 tardanzas' }, f.ausenciasComputadas) },
            { titulo: 'Asistencia', clase: 'columna-avance', celda: (f) => medidorAsistencia(f.porcentaje, resumen.asistenciaMinima) },
            previstas && { titulo: 'Le quedan', clase: 'num', celda: (f) => (f.condicion === 'EXONERADO' ? '–' : `${f.faltasRestantes} faltas`) },
            { titulo: 'Condición', celda: (f) => el('span', { title: f.motivoExoneracion ? `Exonerado: ${f.motivoExoneracion}` : null }, chipCondicion(f.condicion)) },
            esAdmin() && { titulo: '', clase: 'acciones', celda: (f) => {
                if (f.exoneracionId) {
                    return boton('Quitar exoneración', () => eliminarRegistro(`/api/asistencia/exoneraciones/${f.exoneracionId}`,
                        'Quitar exoneración', `${f.nombreEstudiante} volverá a necesitar el ${resumen.asistenciaMinima} % de asistencia en ${materia.codigo}.`),
                    { variante: 'fantasma' });
                }
                return (f.condicion === 'NO_HABILITADO' || f.condicion === 'EN_RIESGO')
                    && boton('Exonerar', () => formularioExoneracion(materia, f), { variante: 'fantasma', iconoNombre: 'escudo' });
            } },
        ].filter(Boolean),
        filas,
        vacio: estadoVacio({ iconoNombre: 'estudiantes', titulo: 'Todavía no hay estudiantes inscriptos',
            accion: boton('Inscribir estudiantes', () => ir(`asistencia?materia=${materia.id}&ver=inscriptos`), { variante: 'primario' }) }),
    }));

    return el('div', { class: 'pila' },
        el('div', { class: 'tarjetas' },
            tarjetaDato({ iconoNombre: 'asistencia', titulo: 'Clases dictadas', valor: resumen.clasesDictadas,
                detalle: previstas ? `de ${resumen.clasesPrevistas} previstas` : 'Sin clases previstas cargadas' }),
            tarjetaDato({ iconoNombre: 'grafico', titulo: 'Asistencia promedio', valor: textoPorcentaje(promedio(porcentajes)) }),
            tarjetaDato({ iconoNombre: 'check', titulo: 'Habilitados para el final', valor: `${habilitados} de ${filas.length}`,
                detalle: cuantos('EXONERADO') ? `${cuantos('EXONERADO')} exonerados` : null }),
            tarjetaDato({ iconoNombre: 'alerta', titulo: 'En riesgo', valor: cuantos('EN_RIESGO') }),
            tarjetaDato({ iconoNombre: 'cerrar', titulo: 'No habilitados', valor: cuantos('NO_HABILITADO') })),
        reglas,
        filas.length > 6 && barraHerramientas(buscador('Buscar estudiante o condición…', () => contenedor)),
        contenedor);
}

function formularioExoneracion(materia, fila) {
    abrirFormulario({
        titulo: `Exonerar de asistencia a ${fila.nombreEstudiante}`,
        descripcion: `En ${materia.codigo} · ${materia.nombre}. Quedará habilitado para el examen final aunque no llegue al mínimo. `
            + 'El reglamento lo permite por causa debidamente justificada, en hasta 2 materias por semestre.',
        campos: [{ nombre: 'motivo', etiqueta: 'Motivo', maxlength: 300, ancho: 'completo', placeholder: 'Ej.: reposo médico con certificado' }],
        textoBoton: 'Exonerar',
        alGuardar: async ({ motivo }) => {
            await api('POST', '/api/asistencia/exoneraciones', { estudianteId: fila.estudianteId, materiaId: materia.id, motivo });
            avisar(`${fila.nombreEstudiante} quedó exonerado de asistencia en ${materia.codigo}.`, 'exito');
            await recargar();
        },
    });
}

async function pestanaInscriptos(materia) {
    const [inscriptos, estudiantes] = await Promise.all([
        api('GET', `/api/inscripciones?materiaId=${materia.id}`),
        api('GET', `/api/estudiantes?carreraId=${materia.carreraId}`),
    ]);
    const yaInscriptos = new Set(inscriptos.map((i) => i.estudianteId));
    const disponibles = estudiantes.filter((e) => e.activo && !yaInscriptos.has(e.id));
    const botonInscribir = (variante = 'primario') => boton('Inscribir estudiantes', () => formularioInscripcion(materia, disponibles),
        { variante, iconoNombre: 'mas' });
    ponerAcciones(
        disponibles.length > 0 && boton('Inscribir a toda la carrera', () => inscribirCarreraCompleta(materia, disponibles.length),
            { iconoNombre: 'estudiantes' }),
        botonInscribir());

    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Estudiante', celda: (i) => persona(i.nombreCompleto, `Matrícula ${i.matricula} · Usuario ${i.username}`) },
            { titulo: 'Inscripto el', celda: (i) => el('span', { class: 'sutil' }, formatearFecha(i.fechaRegistro)) },
            { titulo: 'Registros', celda: (i) => (i.tieneRegistros ? chip('Con notas o asistencia', 'primario') : el('span', { class: 'sutil' }, 'Sin registros')) },
            { titulo: '', clase: 'acciones', celda: (i) => (i.tieneRegistros
                ? ''
                : botonIcono('eliminar', `Quitar a ${i.nombreCompleto} de la materia`, () => eliminarRegistro(`/api/inscripciones/${i.id}`,
                    'Quitar inscripción', `${i.nombreCompleto} dejará de figurar en la lista de asistencia y en la planilla de notas de ${materia.codigo}.`),
                'peligro')) },
        ],
        filas: inscriptos,
        vacio: estadoVacio({
            iconoNombre: 'estudiantes', titulo: 'Nadie inscripto todavía',
            texto: disponibles.length
                ? `Hay ${disponibles.length} estudiantes de ${materia.carreraNombre} para inscribir.`
                : `No hay estudiantes activos en ${materia.carreraNombre}. Agréguelos en Estudiantes.`,
            accion: disponibles.length ? botonInscribir() : boton('Ir a Estudiantes', () => ir('estudiantes'), { variante: 'primario' }),
        }),
    }));

    return el('div', { class: 'pila' },
        el('p', { class: 'herramientas__ayuda' }, icono('info'),
            'Solo los inscriptos aparecen en la lista de asistencia y en la planilla de notas. '
            + 'No se puede quitar a quien ya tiene notas o asistencia registrada.'),
        inscriptos.length > 6 && barraHerramientas(buscador('Buscar inscripto…', () => contenedor)),
        contenedor);
}

async function inscribirCarreraCompleta(materia, cantidad) {
    const aceptar = await confirmar({
        titulo: 'Inscribir a toda la carrera',
        mensaje: `Se inscribirán en ${materia.codigo} los ${cantidad} estudiantes activos de ${materia.carreraNombre} que todavía no la cursan.`,
        textoBoton: 'Inscribir',
    });
    if (!aceptar) return;
    try {
        const resultado = await api('POST', `/api/inscripciones/carrera-completa?materiaId=${materia.id}`);
        avisar(`${resultado.inscriptos === 1 ? '1 estudiante inscripto' : resultado.inscriptos + ' estudiantes inscriptos'} en ${materia.codigo}.`, 'exito');
        await recargar();
    } catch (error) {
        avisar(error.message, 'error');
    }
}

function formularioInscripcion(materia, disponibles) {
    if (!disponibles.length) {
        avisar(`Todos los estudiantes activos de ${materia.carreraNombre} ya están inscriptos en ${materia.codigo}.`);
        return;
    }
    const casillas = disponibles.map((estudiante) => ({ estudiante, entrada: el('input', { type: 'checkbox' }) }));
    const filtro = el('input', { type: 'search', class: 'buscador__entrada', placeholder: 'Buscar por nombre o matrícula…', 'aria-label': 'Buscar estudiante' });
    const lista = el('div', { class: 'lista-seleccion' }, casillas.map(({ estudiante, entrada }) => {
        const item = el('label', { class: 'lista-seleccion__item' }, entrada, persona(estudiante.nombreCompleto, `Matrícula ${estudiante.matricula}`));
        item.dataset.busqueda = normalizar(`${estudiante.nombreCompleto} ${estudiante.matricula} ${estudiante.username}`);
        return item;
    }));
    filtro.addEventListener('input', () => {
        const texto = normalizar(filtro.value.trim());
        for (const item of lista.children) item.hidden = Boolean(texto) && !item.dataset.busqueda.includes(texto);
    });
    filtro.addEventListener('keydown', (evento) => {
        if (evento.key === 'Enter') evento.preventDefault();
    });
    const seleccionarTodos = boton('Marcar todos', () => {
        const visibles = casillas.filter(({ entrada }) => !entrada.closest('label').hidden);
        const marcar = visibles.some(({ entrada }) => !entrada.checked);
        visibles.forEach(({ entrada }) => { entrada.checked = marcar; });
    }, { variante: 'fantasma', iconoNombre: 'check' });

    abrirFormulario({
        titulo: `Inscribir en ${materia.codigo} · ${materia.nombre}`,
        descripcion: `Estudiantes activos de ${materia.carreraNombre} que todavía no cursan la materia.`,
        antes: el('div', { class: 'pila pila--chica' },
            el('div', { class: 'herramientas' }, el('label', { class: 'buscador' }, icono('buscar'), filtro), seleccionarTodos),
            lista),
        campos: [],
        textoBoton: 'Inscribir',
        alGuardar: async () => {
            const ids = casillas.filter(({ entrada }) => entrada.checked).map(({ estudiante }) => estudiante.id);
            if (!ids.length) throw new Error('Marque al menos un estudiante.');
            const resultado = await api('POST', '/api/inscripciones', { materiaId: materia.id, estudianteIds: ids });
            avisar(`${resultado.inscriptos === 1 ? '1 estudiante inscripto' : resultado.inscriptos + ' estudiantes inscriptos'} en ${materia.codigo}.`, 'exito');
            await recargar();
        },
    });
}

// ======================= Reportes =======================

async function vistaReportes() {
    const reporte = await api('GET', '/api/asistencia/reporte');
    if (!reporte.materias.length) {
        return estadoVacio({ iconoNombre: 'grafico', titulo: 'Todavía no hay materias',
            texto: esAdmin() ? 'Cree materias y registre clases para ver los reportes.' : 'Cuando tenga materias a cargo verá aquí sus reportes.' });
    }

    const tarjetas = el('div', { class: 'tarjetas' },
        tarjetaDato({ iconoNombre: 'asistencia', titulo: 'Clases dictadas', valor: reporte.clases }),
        tarjetaDato({ iconoNombre: 'grafico', titulo: 'Asistencia promedio', valor: textoPorcentaje(reporte.promedioAsistencia) }),
        tarjetaDato({ iconoNombre: 'estudiantes', titulo: 'Inscripciones', valor: reporte.inscripciones }),
        tarjetaDato({ iconoNombre: 'alerta', titulo: 'En riesgo', valor: reporte.enRiesgo }),
        tarjetaDato({ iconoNombre: 'cerrar', titulo: 'No habilitados', valor: reporte.noHabilitados,
            detalle: reporte.exonerados ? `${reporte.exonerados} exonerados` : null }));

    const panelCarreras = esAdmin() && panel({
        titulo: 'Por carrera',
        contenido: tabla({
            columnas: [
                { titulo: 'Carrera', celda: (c) => c.nombre },
                { titulo: 'Materias', clase: 'num', celda: (c) => c.materias },
                { titulo: 'Inscripciones', clase: 'num', celda: (c) => c.inscripciones },
                { titulo: 'Asistencia promedio', clase: 'columna-avance', celda: (c) => medidorAsistencia(c.promedioAsistencia, null) },
                { titulo: 'En riesgo', clase: 'num', celda: (c) => c.enRiesgo },
                { titulo: 'No habilitados', clase: 'num', celda: (c) => c.noHabilitados },
            ],
            filas: reporte.carreras,
        }),
    });

    return el('div', { class: 'pila' },
        tarjetas,
        panel({
            titulo: 'Asistencia promedio por materia',
            descripcion: 'Promedio de los estudiantes con clases registradas, de menor a mayor. La línea vertical marca el mínimo de cada materia.',
            contenido: graficoAsistenciaPorMateria(reporte.materias),
        }),
        panel({
            titulo: 'Condición de los estudiantes para el examen final',
            descripcion: 'Cuántos inscriptos de cada materia están habilitados, en riesgo, no habilitados o exonerados.',
            contenido: graficoCondiciones(reporte.materias),
        }),
        panel({
            titulo: 'Estudiantes que necesitan atención',
            descripcion: 'En riesgo o sin derecho al examen final por asistencia, de menor a mayor porcentaje.',
            contenido: tablaAlertas(reporte.alertas),
        }),
        panelCarreras);
}

/** Barras horizontales: asistencia promedio de cada materia con la marca de su mínimo. */
function graficoAsistenciaPorMateria(materias) {
    const ordenadas = [...materias].sort((a, b) => (a.promedioAsistencia ?? 101) - (b.promedioAsistencia ?? 101));
    const itemLeyenda = (tono, texto) => el('span', { class: 'leyenda__item' }, el('span', { class: `leyenda__marca leyenda__marca--${tono}` }), texto);
    return el('div', { class: 'grafico' },
        el('div', { class: 'leyenda' },
            itemLeyenda('exito', 'Holgada'), itemLeyenda('aviso', 'A menos de 10 puntos del mínimo'), itemLeyenda('peligro', 'Debajo del mínimo'),
            el('span', { class: 'leyenda__item' }, el('span', { class: 'leyenda__marca leyenda__marca--minimo' }), 'Mínimo de la materia')),
        el('div', { class: 'grafico-barras', role: 'list' }, ordenadas.map((m) => {
            const tono = tonoAsistencia(m.promedioAsistencia, m.asistenciaMinima);
            const detalle = m.promedioAsistencia === null
                ? `${m.codigo} · ${m.nombre}: todavía sin clases registradas`
                : `${m.codigo} · ${m.nombre}: ${textoPorcentaje(m.promedioAsistencia)} de asistencia promedio (mínimo ${m.asistenciaMinima} %), `
                    + `${m.inscriptos} inscriptos, ${m.clasesDictadas} clases`;
            return el('div', { class: 'grafico-barras__fila', role: 'listitem', title: detalle, tabindex: 0, 'aria-label': detalle },
                el('span', { class: 'grafico-barras__etiqueta' }, el('span', { class: 'grafico-barras__codigo' }, m.codigo),
                    el('span', { class: 'sutil' }, m.nombre)),
                el('div', { class: 'grafico-barras__pista' },
                    m.promedioAsistencia !== null && el('div', {
                        class: `grafico-barras__barra grafico-barras__barra--${tono}`, style: `width: ${Math.max(m.promedioAsistencia, 1)}%`,
                    }),
                    el('div', { class: 'grafico-barras__minimo', style: `left: ${m.asistenciaMinima}%` })),
                el('span', { class: 'grafico-barras__valor' }, m.promedioAsistencia === null ? 'Sin clases' : textoPorcentaje(m.promedioAsistencia)));
        })));
}

/** Barras apiladas al 100 %: condición de los inscriptos de cada materia. */
function graficoCondiciones(materias) {
    const segmentos = [
        ['habilitados', 'Habilitados', 'exito'], ['enRiesgo', 'En riesgo', 'aviso'],
        ['noHabilitados', 'No habilitados', 'peligro'], ['exonerados', 'Exonerados', 'primario'], ['sinClases', 'Sin clases', 'neutro'],
    ];
    return el('div', { class: 'grafico' },
        el('div', { class: 'leyenda' }, segmentos.map(([, texto, tono]) =>
            el('span', { class: 'leyenda__item' }, el('span', { class: `leyenda__marca leyenda__marca--${tono}` }), texto))),
        el('div', { class: 'grafico-barras', role: 'list' }, materias.map((m) => {
            const valores = { ...m, sinClases: m.inscriptos - m.habilitados - m.enRiesgo - m.noHabilitados - m.exonerados };
            const detalle = `${m.codigo} · ${m.nombre}: ` + (m.inscriptos
                ? segmentos.filter(([clave]) => valores[clave] > 0).map(([clave, texto]) => `${valores[clave]} ${texto.toLowerCase()}`).join(', ')
                : 'sin inscriptos');
            return el('div', { class: 'grafico-barras__fila', role: 'listitem', title: detalle, tabindex: 0, 'aria-label': detalle },
                el('span', { class: 'grafico-barras__etiqueta' }, el('span', { class: 'grafico-barras__codigo' }, m.codigo),
                    el('span', { class: 'sutil' }, m.nombre)),
                m.inscriptos
                    ? el('div', { class: 'barra-apilada' }, segmentos.filter(([clave]) => valores[clave] > 0).map(([clave, texto, tono]) => {
                        const ancho = (valores[clave] / m.inscriptos) * 100;
                        return el('div', {
                            class: `barra-apilada__segmento barra-apilada__segmento--${tono}`, style: `flex-grow: ${valores[clave]}`,
                            title: `${valores[clave]} ${texto.toLowerCase()}`,
                        }, ancho >= 9 ? String(valores[clave]) : '');
                    }))
                    : el('div', { class: 'grafico-barras__pista grafico-barras__pista--vacia' }),
                el('span', { class: 'grafico-barras__valor' }, `${m.inscriptos} inscriptos`));
        })));
}

// ======================= Exoneraciones (ADMIN) =======================

async function vistaExoneraciones() {
    const exoneraciones = await api('GET', '/api/asistencia/exoneraciones');
    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Estudiante', celda: (x) => persona(x.nombreEstudiante, `Matrícula ${x.matricula} · ${x.carreraNombre}`) },
            { titulo: 'Materia', celda: (x) => el('div', { class: 'celda-doble' }, el('span', {}, x.nombreMateria),
                el('span', { class: 'sutil' }, `${x.codigoMateria} · ${x.semestre}° semestre`)) },
            { titulo: 'Motivo', celda: (x) => x.motivo },
            { titulo: 'Otorgada', celda: (x) => el('span', { class: 'sutil' }, `${formatearFecha(x.fechaRegistro)} por ${x.otorgadaPor}`) },
            { titulo: '', clase: 'acciones', celda: (x) => [
                boton('Ver materia', () => ir(`asistencia?materia=${x.materiaId}&ver=resumen`), { variante: 'fantasma', iconoNombre: 'flecha' }),
                botonIcono('eliminar', `Quitar la exoneración de ${x.nombreEstudiante}`, () => eliminarRegistro(
                    `/api/asistencia/exoneraciones/${x.id}`, 'Quitar exoneración',
                    `${x.nombreEstudiante} volverá a necesitar la asistencia mínima en ${x.codigoMateria}.`), 'peligro'),
            ] },
        ],
        filas: exoneraciones,
        vacio: estadoVacio({ iconoNombre: 'escudo', titulo: 'No hay exoneraciones',
            texto: 'Se otorgan desde Asistencia → "Asistencia y habilitados" de cada materia, con el botón Exonerar.' }),
    }));
    return el('div', { class: 'pila' },
        el('div', { class: 'resultado' }, icono('info'), el('span', {},
            'Por causa debidamente justificada, un estudiante puede ser exonerado de asistencia en hasta 2 materias por semestre: '
            + 'queda habilitado para el examen final aunque no llegue al mínimo. Para otorgarla, abra la materia en Asistencia → "Asistencia y habilitados".')),
        exoneraciones.length > 6 && barraHerramientas(buscador('Buscar…', () => contenedor)),
        contenedor);
}

// ======================= Ajustes =======================

function vistaAjustes() {
    let preferencias = temaActual();
    const actualizadores = [];
    const estadoGuardado = el('span', { class: 'guardado', 'aria-live': 'polite' });
    let temporizador;

    function cambiar(cambio) {
        preferencias = { ...preferencias, ...cambio };
        aplicarTema(preferencias);
        recordarTema(estado.sesion.username, preferencias);
        actualizadores.forEach((actualizar) => actualizar());
        estadoGuardado.replaceChildren('Guardando…');
        estadoGuardado.className = 'guardado';
        clearTimeout(temporizador);
        temporizador = setTimeout(async () => {
            try {
                await api('PUT', '/api/perfil/preferencias', preferencias);
                if (estado.perfil) estado.perfil.preferencias = preferencias;
                estadoGuardado.replaceChildren(icono('check'), 'Cambios guardados en su cuenta');
                estadoGuardado.className = 'guardado guardado--ok';
            } catch (error) {
                estadoGuardado.replaceChildren('');
                avisar(error.message, 'error');
            }
        }, 450);
    }

    /** Botones de opción única (segmentado). */
    function grupoOpciones(clave, opciones) {
        const botones = opciones.map(([valor, texto, nombreIcono]) => el('button', {
            type: 'button', class: 'segmento', onclick: () => cambiar({ [clave]: valor }),
        }, nombreIcono && icono(nombreIcono), texto));
        actualizadores.push(() => botones.forEach((b, i) => b.setAttribute('aria-pressed', String(preferencias[clave] === opciones[i][0]))));
        return el('div', { class: 'segmentado', role: 'group' }, botones);
    }

    /** Muestras de color más un selector para elegir cualquier color. */
    function paleta(clave, colores) {
        const muestras = colores.map(([nombre, color]) => el('button', {
            type: 'button', class: 'muestra', style: `--muestra: ${color}`, title: nombre, 'aria-label': nombre,
            onclick: () => cambiar({ [clave]: color }),
        }, icono('check', 'icono muestra__check')));
        const selector = el('input', { type: 'color', class: 'muestra__entrada', 'aria-label': 'Color personalizado' });
        selector.addEventListener('input', () => cambiar({ [clave]: selector.value }));
        const personalizada = el('label', { class: 'muestra muestra--personalizada', title: 'Color personalizado' }, icono('paleta'), selector);
        actualizadores.push(() => {
            const valor = preferencias[clave].toLowerCase();
            let coincide = false;
            muestras.forEach((m, i) => {
                const elegida = colores[i][1].toLowerCase() === valor;
                coincide ||= elegida;
                m.setAttribute('aria-pressed', String(elegida));
            });
            selector.value = valor;
            personalizada.classList.toggle('elegida', !coincide);
            personalizada.style.setProperty('--muestra', coincide ? 'transparent' : valor);
        });
        return el('div', { class: 'paleta' }, muestras, personalizada);
    }

    const opcion = (titulo, descripcion, control) => el('div', { class: 'ajuste' },
        el('div', { class: 'ajuste__textos' }, el('span', { class: 'ajuste__titulo' }, titulo),
            el('span', { class: 'ajuste__descripcion' }, descripcion)),
        control);

    const apariencia = panel({
        titulo: 'Apariencia',
        descripcion: 'Se aplica al instante y se guarda en su cuenta: lo verá igual en cualquier computadora.',
        acciones: [estadoGuardado, boton('Restablecer', () => cambiar({ ...TEMA_POR_DEFECTO }), { variante: 'fantasma', iconoNombre: 'deshacer' })],
        contenido: el('div', { class: 'ajustes' },
            opcion('Modo', 'Fondo oscuro o claro. "Automático" sigue la configuración de Windows.',
                grupoOpciones('modo', [['oscuro', 'Oscuro', 'luna'], ['claro', 'Claro', 'sol'], ['sistema', 'Automático', 'monitor']])),
            opcion('Color principal', 'Botones, enlaces y resaltados.', paleta('colorPrimario', COLORES_PRIMARIOS)),
            opcion('Barra lateral', 'Color del menú de la izquierda.', paleta('colorBarra', COLORES_BARRA)),
            opcion('Densidad', 'Espacio entre filas de las tablas y los formularios.',
                grupoOpciones('densidad', [['comoda', 'Cómoda'], ['compacta', 'Compacta']])),
            opcion('Tamaño del texto', 'Agrandar todo el texto de la interfaz.',
                grupoOpciones('tamanoTexto', [['normal', 'Normal'], ['grande', 'Grande']]))),
    });
    actualizadores.forEach((actualizar) => actualizar());

    const nombre = estado.perfil?.nombre ?? estado.sesion.username;
    const cuenta = panel({
        titulo: 'Mi cuenta',
        contenido: el('div', { class: 'cuenta' },
            el('div', { class: 'cuenta__perfil' }, avatar(nombre, 'avatar--grande'),
                el('div', { class: 'celda-doble' }, el('span', { class: 'cuenta__nombre' }, nombre),
                    el('span', { class: 'sutil' }, `${estado.sesion.username} · ${NOMBRES_ROL[estado.sesion.rol]}`))),
            boton('Cambiar mi contraseña', () => formularioMiContrasena(), { iconoNombre: 'candado' })),
    });

    return el('div', { class: 'pila' }, apariencia, panelSeguridad(), cuenta);
}

function panelSeguridad() {
    const activa = Boolean(estado.perfil?.dosFactores);
    return panel({
        titulo: 'Verificación en dos pasos',
        descripcion: 'Además de la contraseña, al ingresar se pide un código de 6 dígitos de Google Authenticator '
            + '(o Microsoft Authenticator, Authy…). Aunque alguien conozca su contraseña, sin su celular no puede entrar.',
        acciones: chip(activa ? 'Activada' : 'Desactivada', activa ? 'exito' : 'neutro'),
        contenido: el('div', { class: 'cuenta' },
            el('div', { class: 'cuenta__perfil' },
                el('div', { class: 'dato__icono' }, icono(activa ? 'usuarios' : 'escudo')),
                el('span', { class: 'sutil' }, activa
                    ? 'Su cuenta está protegida. Si cambia de celular, desactívela y vuelva a activarla.'
                    : 'Recomendada para administradores y docentes.')),
            activa
                ? boton('Desactivar', () => formularioDesactivarDosFactores(), { iconoNombre: 'candado' })
                : boton('Activar con Google Authenticator', () => asistenteDosFactores(), { variante: 'primario', iconoNombre: 'celular' })),
    });
}

async function asistenteDosFactores() {
    let datos;
    try {
        datos = await api('POST', '/api/perfil/2fa/iniciar');
    } catch (error) {
        avisar(error.message, 'error');
        return;
    }
    const clave = datos.secreto.match(/.{1,4}/g).join(' ');

    // Reloj del sistema en vivo: debe coincidir con la hora del celular (los códigos dependen de la hora)
    const desfase = datos.horaServidor - Date.now();
    const reloj = el('strong', { class: 'dosfa__reloj' });
    const actualizarReloj = () => {
        reloj.textContent = new Date(Date.now() + desfase).toLocaleTimeString('es-PY', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    };
    actualizarReloj();
    const intervalo = setInterval(actualizarReloj, 1000);
    $('#modal').addEventListener('close', () => clearInterval(intervalo), { once: true });

    abrirFormulario({
        titulo: 'Activar verificación en dos pasos',
        antes: el('div', { class: 'dosfa' },
            el('div', { class: 'dosfa__textos' },
                el('ol', { class: 'dosfa__pasos' },
                    el('li', {}, 'Instale ', el('strong', {}, 'Google Authenticator'), ' en su celular (Play Store o App Store).'),
                    el('li', {}, 'Toque ', el('strong', {}, '+'), ' → ', el('strong', {}, 'Escanear un código QR'), ' y apunte a este código.'),
                    el('li', {}, 'Escriba abajo el código de 6 dígitos que aparece en la app.')),
                el('div', { class: 'dosfa__nota' }, icono('info'), el('span', {},
                    '¿Ya lo intentó antes? En la app borre las entradas viejas de ', el('strong', {}, 'Seguimiento Académico UPE'),
                    ' y use solo la que agregue ahora.')),
                el('div', { class: 'dosfa__nota' }, icono('info'), el('span', {},
                    'Hora del sistema: ', reloj, ' — la del celular debe coincidir (con la hora en automático).'))),
            el('div', { class: 'dosfa__qr' },
                el('img', { src: datos.qr, alt: 'Código QR para Google Authenticator', width: 184, height: 184 }),
                el('details', { class: 'dosfa__clave' },
                    el('summary', {}, '¿No puede escanear? Cargue la clave a mano'),
                    el('code', {}, clave)))),
        campos: [{
            nombre: 'codigo', etiqueta: 'Código de 6 dígitos', ancho: 'completo', placeholder: '000000',
            inputmode: 'numeric', autocomplete: 'one-time-code', pattern: '\\d{6}', minlength: 6, maxlength: 6,
            clase: 'entrada-codigo',
        }],
        textoBoton: 'Activar',
        alGuardar: async ({ codigo }) => {
            await api('POST', '/api/perfil/2fa/activar', { codigo: codigo.trim() });
            if (estado.perfil) estado.perfil.dosFactores = true;
            avisar('Verificación en dos pasos activada. La próxima vez se le pedirá el código.', 'exito');
            await recargar();
        },
    });
}

function formularioDesactivarDosFactores() {
    abrirFormulario({
        titulo: 'Desactivar verificación en dos pasos',
        descripcion: 'Por seguridad, confirme con su contraseña y un código de la app.',
        campos: [
            { nombre: 'password', etiqueta: 'Contraseña', tipo: 'password', autocomplete: 'current-password', ancho: 'completo' },
            { nombre: 'codigo', etiqueta: 'Código de 6 dígitos', ancho: 'completo', placeholder: '000000', inputmode: 'numeric',
                autocomplete: 'one-time-code', pattern: '\\d{6}', minlength: 6, maxlength: 6, clase: 'entrada-codigo' },
        ],
        textoBoton: 'Desactivar',
        alGuardar: async ({ password, codigo }) => {
            await api('POST', '/api/perfil/2fa/desactivar', { password, codigo: codigo.trim() });
            if (estado.perfil) estado.perfil.dosFactores = false;
            avisar('Verificación en dos pasos desactivada.', 'exito');
            await recargar();
        },
    });
}

// ======================= Carga masiva (ADMIN) =======================

async function vistaCargaMasiva() {
    const carreras = await api('GET', '/api/carreras');
    if (!carreras.length) {
        return estadoVacio({ iconoNombre: 'carreras', titulo: 'Primero cree una carrera',
            texto: 'La plantilla se genera por carrera, con filas preparadas para cada uno de sus semestres.',
            accion: boton('Ir a Carreras', () => ir('carreras'), { variante: 'primario' }) });
    }

    // Paso 1: plantilla
    let carreraElegida = String(carreras[0].id);
    const botonPlantilla = () => botonExcel('Descargar plantilla', `/api/carga-masiva/plantilla?carreraId=${carreraElegida}`, 'primario');
    const contenedorBoton = el('div', {}, botonPlantilla());
    const paso1 = panel({
        titulo: '1. Descargar la plantilla de la carrera',
        descripcion: 'Un Excel con hojas para Docentes, Materias (con filas listas para cada semestre) y Estudiantes. '
            + 'Lo ya registrado aparece en gris; las columnas tienen listas desplegables.',
        contenido: el('div', { class: 'herramientas' },
            selectorFiltro('Carrera', carreras.map((c) => ({ valor: c.id, texto: `${c.nombre} · ${c.semestres} semestres` })),
                carreraElegida, (valor) => {
                    carreraElegida = valor;
                    contenedorBoton.replaceChildren(botonPlantilla());
                }),
            contenedorBoton),
    });

    // Paso 2: subir, validar e importar
    const resultado = el('div');
    const entrada = el('input', { type: 'file', accept: '.xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', class: 'zona-archivo__entrada' });
    const textoZona = el('span', { class: 'zona-archivo__texto' }, 'Arrastre aquí la plantilla completa o haga clic para elegirla');
    const zona = el('label', { class: 'zona-archivo' }, icono('subir', 'icono zona-archivo__icono'), textoZona,
        el('span', { class: 'sutil' }, 'Archivo .xlsx de hasta 5 MB'), entrada);

    async function procesar(archivo, soloValidar) {
        if (!archivo) return;
        textoZona.textContent = archivo.name;
        zona.classList.add('zona-archivo--cargada');
        resultado.replaceChildren(el('div', { class: 'panel' }, el('div', { class: 'esqueleto', style: 'width: 60%' })));
        try {
            const datos = await subirArchivo(`/api/carga-masiva?soloValidar=${soloValidar}`, archivo);
            resultado.replaceChildren(resultadoImportacion(datos, () => procesar(archivo, false)));
            resultado.scrollIntoView({ behavior: 'smooth', block: 'start' });
            if (datos.aplicado) avisar(`Carga completada en ${datos.carrera}.`, 'exito');
        } catch (error) {
            resultado.replaceChildren(estadoVacio({ iconoNombre: 'alerta', titulo: 'No se pudo procesar el archivo', texto: error.message }));
        }
    }

    entrada.addEventListener('change', () => {
        const archivo = entrada.files[0];
        entrada.value = ''; // permite volver a elegir el mismo archivo después de corregirlo
        procesar(archivo, true);
    });
    zona.addEventListener('dragover', (evento) => {
        evento.preventDefault();
        zona.classList.add('zona-archivo--encima');
    });
    zona.addEventListener('dragleave', () => zona.classList.remove('zona-archivo--encima'));
    zona.addEventListener('drop', (evento) => {
        evento.preventDefault();
        zona.classList.remove('zona-archivo--encima');
        procesar(evento.dataTransfer.files[0], true);
    });

    const paso2 = panel({
        titulo: '2. Subir la plantilla completa',
        descripcion: 'Primero se revisa todo sin guardar nada. Si no hay errores, se confirma la carga. '
            + 'Si hay un solo error, no se guarda nada: se corrige el Excel y se vuelve a subir.',
        contenido: zona,
    });

    return el('div', { class: 'pila' }, paso1, paso2, resultado);
}

function resultadoImportacion(datos, importar) {
    const errores = datos.observaciones.filter((o) => o.tipo === 'ERROR');
    const omitidos = datos.observaciones.filter((o) => o.tipo === 'OMITIDO');
    const nuevos = datos.docentes + datos.materias + datos.estudiantes;
    const verbo = datos.aplicado ? 'Creados' : 'A crear';

    let estadoFinal;
    if (errores.length) {
        estadoFinal = el('div', { class: 'resultado resultado--error' }, icono('alerta'),
            el('span', {}, `Hay ${errores.length === 1 ? '1 fila con errores' : errores.length + ' filas con errores'}. `
                + 'Corríjalas en el Excel y vuelva a subirlo. No se guardó nada.'));
    } else if (datos.aplicado) {
        estadoFinal = el('div', { class: 'resultado resultado--ok' }, icono('check'),
            el('span', {}, nuevos ? `Listo: se cargaron ${nuevos} registros en ${datos.carrera}.` : 'No había nada nuevo para cargar.'));
    } else {
        estadoFinal = el('div', { class: 'resultado' }, icono('check'),
            el('span', {}, nuevos ? 'El archivo está correcto. Revise el resumen y confirme la carga.' : 'El archivo está correcto, pero no tiene filas nuevas.'),
            nuevos > 0 && boton(`Confirmar e importar ${nuevos} registros`, async (evento) => {
                evento.currentTarget.disabled = true;
                await importar();
            }, { variante: 'primario', iconoNombre: 'subir' }));
    }

    const tablaObservaciones = (lista) => tabla({
        columnas: [
            { titulo: 'Hoja', celda: (o) => o.hoja },
            { titulo: 'Fila', clase: 'num', celda: (o) => o.fila },
            { titulo: 'Detalle', celda: (o) => o.mensaje },
        ],
        filas: lista,
    });

    return panel({
        titulo: `Resultado · ${datos.carrera}`,
        contenido: el('div', { class: 'pila' },
            el('div', { class: 'tarjetas' },
                tarjetaDato({ iconoNombre: 'docentes', titulo: `${verbo}: docentes`, valor: datos.docentes }),
                tarjetaDato({ iconoNombre: 'materias', titulo: `${verbo}: materias`, valor: datos.materias }),
                tarjetaDato({ iconoNombre: 'estudiantes', titulo: `${verbo}: estudiantes`, valor: datos.estudiantes }),
                tarjetaDato({ iconoNombre: 'info', titulo: 'Ya existían (se omiten)', valor: datos.omitidos }),
                tarjetaDato({ iconoNombre: 'alerta', titulo: 'Filas con errores', valor: datos.errores })),
            estadoFinal,
            errores.length > 0 && tablaObservaciones(errores),
            omitidos.length > 0 && el('details', { class: 'detalles' },
                el('summary', {}, `Ver ${omitidos.length} filas omitidas porque ya existían`),
                tablaObservaciones(omitidos))),
    });
}

function formularioMiContrasena() {
    abrirFormulario({
        titulo: 'Cambiar mi contraseña',
        campos: [
            { nombre: 'actual', etiqueta: 'Contraseña actual', tipo: 'password', ancho: 'completo' },
            { nombre: 'nueva', etiqueta: 'Contraseña nueva', tipo: 'password', minlength: 6, maxlength: 72, ancho: 'completo', ayuda: 'Mínimo 6 caracteres' },
            { nombre: 'repetir', etiqueta: 'Repetir la contraseña nueva', tipo: 'password', minlength: 6, maxlength: 72, ancho: 'completo' },
        ],
        textoBoton: 'Cambiar contraseña',
        alGuardar: async (datos) => {
            if (datos.nueva !== datos.repetir) throw new Error('Las contraseñas nuevas no coinciden.');
            await api('PUT', '/api/perfil/contrasena', { actual: datos.actual, nueva: datos.nueva });
            avisar('Su contraseña fue cambiada.', 'exito');
        },
    });
}

// ======================= Inicio de la aplicación =======================

alExpirarSesion(() => cerrarSesion('La sesión expiró. Ingrese de nuevo.'));
$('#form-login').addEventListener('submit', ingresar);
$('#login-volver').addEventListener('click', () => pasoDeLogin(false));
$('#boton-salir').addEventListener('click', async () => {
    if (await puedeSalir()) cerrarSesion();
});
$('#boton-menu').addEventListener('click', () => document.body.classList.toggle('menu-abierto'));
$('#fondo-lateral').addEventListener('click', () => document.body.classList.remove('menu-abierto'));
$('#modal').addEventListener('click', (evento) => {
    if (evento.target === evento.currentTarget) cerrarModal();
});
window.addEventListener('hashchange', alCambiarRuta);
window.addEventListener('beforeunload', (evento) => {
    if (cambiosSinGuardar()) evento.preventDefault();
});

estado.sesion = leerSesion();
if (estado.sesion) iniciarApp();
else mostrarLogin();
