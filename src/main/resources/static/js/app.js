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
    notas: { titulo: 'Notas', icono: 'notas', roles: GESTION, render: vistaNotas, subtitulo: 'Planilla de notas por materia' },
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
    usuarios: { titulo: 'Usuarios', icono: 'usuarios', roles: ['ADMIN'], render: vistaUsuarios, subtitulo: 'Cuentas de acceso, activación, contraseñas y verificación en dos pasos' },
    ajustes: { titulo: 'Ajustes', icono: 'ajustes', roles: TODOS, render: vistaAjustes, subtitulo: 'Apariencia, seguridad y datos de su cuenta' },
};
const MENU_PRINCIPAL = ['inicio', 'misNotas', 'notas', 'estudiantes', 'materias', 'docentes', 'carreras', 'cargaMasiva', 'usuarios'];

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
        titulo: 'Hay notas sin guardar',
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
    const peticiones = [api('GET', '/api/materias'), api('GET', '/api/estudiantes'), api('GET', '/api/calificaciones')];
    if (esAdmin()) peticiones.push(api('GET', '/api/carreras'), api('GET', '/api/docentes'));
    const [materias, estudiantes, notas, carreras, docentes] = await Promise.all(peticiones);
    ponerAcciones(botonExcel(esAdmin() ? 'Exportar todas las notas' : 'Exportar mis notas cargadas', '/api/exportar/notas'));

    const promedioGeneral = promedio(notas.map((nota) => nota.nota));
    const sinDocente = materias.filter((materia) => !materia.docenteId).length;
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
        ]
        : [
            tarjetaDato({ iconoNombre: 'materias', titulo: 'Mis materias', valor: materias.length }),
            tarjetaDato({ iconoNombre: 'estudiantes', titulo: 'Mis estudiantes', valor: estudiantes.length }),
            tarjetaDato({ iconoNombre: 'notas', titulo: 'Notas cargadas', valor: notas.length }),
            tarjetaDato({ iconoNombre: 'grafico', titulo: 'Promedio', valor: formatearNumero(promedioGeneral) }),
        ];

    const avance = materias.map((materia) => {
        const notasDeMateria = notas.filter((nota) => nota.materiaId === materia.id);
        return {
            materia,
            total: estudiantes.filter((estudiante) => estudiante.carreraId === materia.carreraId).length,
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
        { titulo: '', clase: 'acciones', celda: ({ materia }) =>
            boton('Planilla', () => ir(`notas?materia=${materia.id}`), { variante: 'fantasma', iconoNombre: 'flecha' }) },
    ].filter(Boolean);

    const vacio = esAdmin()
        ? estadoVacio({ iconoNombre: 'materias', titulo: 'Todavía no hay materias', texto: 'Empiece creando las carreras, los docentes y las materias.',
            accion: boton('Ir a Carreras', () => ir('carreras'), { variante: 'primario' }) })
        : estadoVacio({ iconoNombre: 'materias', titulo: 'Todavía no tiene materias a cargo', texto: 'Pida al administrador que le asigne sus materias.' });

    return el('div', { class: 'pila' },
        el('div', { class: 'tarjetas' }, tarjetas),
        panel({
            titulo: 'Avance de carga de notas',
            descripcion: 'Estudiantes con nota sobre el total de estudiantes de la carrera de cada materia.',
            contenido: tabla({ columnas, filas: avance, vacio }),
        }));
}

// ======================= Notas (planilla por materia) =======================

async function vistaNotas(parametros) {
    const [materias, estudiantes] = await Promise.all([api('GET', '/api/materias'), api('GET', '/api/estudiantes')]);
    if (!materias.length) {
        return esAdmin()
            ? estadoVacio({ iconoNombre: 'materias', titulo: 'Todavía no hay materias', texto: 'Cree las carreras y materias para poder cargar notas.',
                accion: boton('Ir a Materias', () => ir('materias'), { variante: 'primario' }) })
            : estadoVacio({ iconoNombre: 'notas', titulo: 'Todavía no tiene materias a cargo',
                texto: 'Pida al administrador que le asigne sus materias para poder cargar notas.' });
    }

    const materia = materias.find((m) => String(m.id) === parametros.get('materia'));
    const selector = selectorMateria(materias, materia?.id);
    if (!materia) {
        return el('div', { class: 'pila' },
            panel({ contenido: selector, clase: 'panel--selector' }),
            estadoVacio({ iconoNombre: 'notas', titulo: 'Elija una materia',
                texto: 'Se mostrará la planilla con todos los estudiantes de la carrera para cargar o corregir sus notas.' }));
    }

    const notas = await api('GET', `/api/calificaciones?materiaId=${materia.id}`);
    const alumnos = estudiantes.filter((estudiante) => estudiante.carreraId === materia.carreraId);
    return planillaNotas(materia, alumnos, notas, selector);
}

function selectorMateria(materias, seleccionada) {
    const porCarrera = new Map();
    for (const materia of materias) {
        if (!porCarrera.has(materia.carreraNombre)) porCarrera.set(materia.carreraNombre, []);
        porCarrera.get(materia.carreraNombre).push(materia);
    }
    const select = el('select', {
        class: 'selector-materia__select', 'aria-label': 'Materia',
        onchange: (evento) => ir(conParametro('notas', 'materia', evento.target.value)),
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
    invalida: ['Debe ser de 0 a 100', 'peligro'],
    vaciada: ['Para borrarla use Eliminar', 'peligro'],
};

function planillaNotas(materia, alumnos, notas, selector) {
    const porEstudiante = new Map(notas.map((nota) => [nota.estudianteId, nota]));
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

    function calcularEstado(fila) {
        const texto = fila.entrada.value.trim();
        const original = fila.nota ? String(fila.nota.nota) : '';
        if (texto === original) return fila.nota ? 'guardada' : 'vacia';
        if (texto === '') return 'vaciada';
        const valor = Number(texto);
        return Number.isInteger(valor) && valor >= 0 && valor <= 100 ? 'modificada' : 'invalida';
    }

    function actualizarFila(fila) {
        fila.estado = calcularEstado(fila);
        fila.tr.dataset.estado = fila.estado;
        fila.celdaEstado.replaceChildren(chip(...ESTADOS_NOTA[fila.estado]));
        fila.celdaFecha.textContent = fila.nota ? formatearFecha(fila.nota.fechaActualizacion) : '';
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

    const cuerpo = el('tbody');
    alumnos.forEach((alumno, indice) => {
        const entrada = el('input', {
            type: 'number', class: 'planilla__nota', min: 0, max: 100, step: 1, inputmode: 'numeric',
            'aria-label': `Nota de ${alumno.nombreCompleto}`,
        });
        const fila = {
            alumno, nota: porEstudiante.get(alumno.id) ?? null, entrada,
            celdaEstado: el('td'), celdaFecha: el('td', { class: 'sutil' }), celdaAcciones: el('td', { class: 'acciones' }),
        };
        entrada.value = fila.nota ? fila.nota.nota : '';
        entrada.addEventListener('input', () => {
            actualizarFila(fila);
            actualizarResumen();
        });
        // Enter pasa al siguiente estudiante visible para cargar rápido
        entrada.addEventListener('keydown', (evento) => {
            if (evento.key !== 'Enter') return;
            evento.preventDefault();
            const siguiente = filas.slice(indice + 1).find((otra) => !otra.tr.hidden);
            if (siguiente) {
                siguiente.entrada.focus();
                siguiente.entrada.select();
            } else if (pendientes().length) {
                guardar();
            }
        });
        fila.tr = el('tr', {},
            el('td', {}, persona(alumno.nombreCompleto, `Matrícula ${alumno.matricula}`)),
            el('td', {}, entrada), fila.celdaEstado, fila.celdaFecha, fila.celdaAcciones);
        fila.tr.dataset.busqueda = normalizar(`${alumno.nombreCompleto} ${alumno.matricula} ${alumno.username}`);
        filas.push(fila);
        actualizarFila(fila);
        cuerpo.append(fila.tr);
    });
    cuerpo.append(el('tr', { class: 'tabla__sin-resultados', hidden: true },
        el('td', { colspan: 5 }, 'No hay resultados para esa búsqueda.')));
    actualizarResumen();

    async function guardar() {
        const cambios = pendientes();
        if (!cambios.length) return;
        botonGuardar.disabled = true;
        botonGuardar.classList.add('boton--ocupado');
        const resultados = await Promise.allSettled(cambios.map((fila) => {
            const nota = Number(fila.entrada.value);
            return fila.nota
                ? api('PATCH', `/api/calificaciones/${fila.nota.id}`, { nota })
                : api('POST', '/api/calificaciones', { estudianteId: fila.alumno.id, materiaId: materia.id, nota });
        }));
        botonGuardar.classList.remove('boton--ocupado');
        const errores = [];
        resultados.forEach((resultado, i) => {
            if (resultado.status === 'fulfilled') {
                cambios[i].nota = resultado.value;
                cambios[i].entrada.value = resultado.value.nota;
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
            fila.entrada.value = fila.nota ? fila.nota.nota : '';
            actualizarFila(fila);
        }
        actualizarResumen();
    }

    async function eliminarNota(fila) {
        const aceptar = await confirmar({
            titulo: 'Eliminar nota',
            mensaje: `Se eliminará la nota ${fila.nota.nota} de ${fila.alumno.nombreCompleto} en ${materia.codigo}.`,
            textoBoton: 'Eliminar', peligro: true,
        });
        if (!aceptar) return;
        try {
            await api('DELETE', `/api/calificaciones/${fila.nota.id}`);
            fila.nota = null;
            fila.entrada.value = '';
            actualizarFila(fila);
            actualizarResumen();
            avisar('Nota eliminada.', 'exito');
        } catch (error) {
            avisar(error.message, 'error');
        }
    }

    const tablaPlanilla = alumnos.length
        ? el('div', { class: 'tabla-contenedor' },
            el('table', { class: 'tabla planilla' },
                el('thead', {}, el('tr', {},
                    el('th', {}, 'Estudiante'), el('th', {}, 'Nota'), el('th', {}, 'Estado'),
                    el('th', {}, 'Actualizada'), el('th', { class: 'acciones' }, ''))),
                cuerpo))
        : estadoVacio({
            iconoNombre: 'estudiantes', titulo: `No hay estudiantes en ${materia.carreraNombre}`,
            texto: 'Agregue los estudiantes de esta carrera para poder cargarles notas.',
            accion: boton('Ir a Estudiantes', () => ir('estudiantes'), { variante: 'primario' }),
        });
    const contenedor = el('div', {}, tablaPlanilla);

    return el('div', { class: 'pila' },
        panel({ contenido: el('div', { class: 'planilla__cabecera' }, selector, resumen), clase: 'panel--selector' }),
        alumnos.length && barraHerramientas(
            buscador('Buscar estudiante…', () => contenedor),
            el('span', { class: 'herramientas__ayuda' }, icono('info'), 'Escriba la nota y presione Enter para pasar al siguiente.'),
            el('span', { class: 'herramientas__empuje' }),
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
            esAdmin() && { titulo: 'Docente', celda: (m) => (m.docenteNombre ? persona(m.docenteNombre, m.docenteUsername) : chip('Sin asignar', 'aviso')) },
            { titulo: '', clase: 'acciones', celda: (m) => (esAdmin()
                ? [
                    botonIcono('editar', `Editar ${m.codigo}`, () => formularioMateria(carreras, docentes, m)),
                    botonIcono('eliminar', `Eliminar ${m.codigo}`, () => eliminarRegistro(`/api/materias/${m.id}`,
                        'Eliminar materia', `Se eliminará ${m.codigo} · ${m.nombre}. Solo es posible si no tiene notas.`), 'peligro'),
                ]
                : boton('Cargar notas', () => ir(`notas?materia=${m.id}`), { variante: 'fantasma', iconoNombre: 'flecha' })) },
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
                ayuda: 'Solo el docente a cargo puede cargar las notas de esta materia' },
        ],
        valores: materia ?? { carreraId: carreras.length === 1 ? carreras[0].id : '' },
        textoBoton: materia ? 'Guardar cambios' : 'Crear materia',
        alGuardar: async (datos) => {
            const cuerpo = {
                codigo: datos.codigo, nombre: datos.nombre, creditos: Number(datos.creditos),
                semestre: Number(datos.semestre), carreraId: Number(datos.carreraId),
                docenteId: datos.docenteId ? Number(datos.docenteId) : null,
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
        ],
        valores: carrera ?? { semestres: 10 },
        textoBoton: carrera ? 'Guardar cambios' : 'Crear carrera',
        alGuardar: async (datos) => {
            await api(carrera ? 'PUT' : 'POST', carrera ? `/api/carreras/${carrera.id}` : '/api/carreras',
                { codigo: datos.codigo, nombre: datos.nombre, semestres: Number(datos.semestres) });
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

    const contenedor = el('div', {}, tabla({
        columnas: [
            { titulo: 'Materia', celda: (n) => el('div', { class: 'celda-doble' }, el('span', {}, n.nombreMateria), el('span', { class: 'sutil' }, n.codigoMateria)) },
            { titulo: 'Docente', celda: (n) => n.docenteNombre ?? '—' },
            { titulo: 'Semestre', clase: 'num', celda: (n) => `${n.semestre}°` },
            { titulo: 'Créditos', clase: 'num', celda: (n) => n.creditos },
            { titulo: 'Nota', clase: 'columna-avance', celda: (n) => el('div', { class: 'medidor' },
                el('span', { class: 'medidor__valor' }, n.nota),
                el('div', { class: 'medidor__pista' }, el('div', { class: 'medidor__relleno', style: `width: ${n.nota}%` }))) },
            { titulo: 'Actualizada', celda: (n) => el('span', { class: 'sutil' }, formatearFecha(n.fechaActualizacion)) },
        ],
        filas: notas,
        vacio: estadoVacio({ iconoNombre: 'notas', titulo: 'Todavía no tiene notas', texto: 'Cuando sus docentes carguen notas aparecerán aquí.' }),
    }));

    return el('div', { class: 'pila' },
        el('div', { class: 'tarjetas' },
            tarjetaDato({ iconoNombre: 'grafico', titulo: 'Promedio', valor: formatearNumero(promedio(valores)) }),
            tarjetaDato({ iconoNombre: 'materias', titulo: 'Materias con nota', valor: notas.length }),
            tarjetaDato({ iconoNombre: 'carreras', titulo: 'Créditos cursados', valor: notas.reduce((suma, n) => suma + n.creditos, 0) }),
            tarjetaDato({ iconoNombre: 'estrella', titulo: 'Mejor nota', valor: valores.length ? Math.max(...valores) : '–' })),
        notas.length > 6 && barraHerramientas(buscador('Buscar materia…', () => contenedor)),
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
