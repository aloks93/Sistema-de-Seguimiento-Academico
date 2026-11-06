// Componentes de la interfaz: íconos, botones, avisos, ventanas modales, formularios y tablas.

import { $, el, iniciales, normalizar } from './nucleo.js';

// ======================= Íconos (trazos de 24x24) =======================

const ICONOS = {
    inicio: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/><path d="M10 21v-6h4v6"/>',
    notas: '<rect x="8" y="2" width="8" height="4" rx="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"/><path d="m9 14 2 2 4-4"/>',
    estudiantes: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
    materias: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>',
    docentes: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="m16 11 2 2 4-4"/>',
    carreras: '<path d="M22 10 12 5 2 10l10 5 10-5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/><path d="M22 10v6"/>',
    usuarios: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/>',
    ajustes: '<path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"/>',
    salir: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>',
    menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
    buscar: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
    mas: '<path d="M12 5v14M5 12h14"/>',
    editar: '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/>',
    eliminar: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
    cerrar: '<path d="M18 6 6 18M6 6l12 12"/>',
    check: '<path d="M20 6 9 17l-5-5"/>',
    alerta: '<circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/>',
    info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>',
    llave: '<circle cx="7.5" cy="15.5" r="5.5"/><path d="m21 2-9.6 9.6M15.5 7.5l3 3L22 7l-3-3"/>',
    candado: '<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
    usuario: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
    sol: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/>',
    luna: '<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>',
    monitor: '<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/>',
    paleta: '<circle cx="13.5" cy="6.5" r=".5"/><circle cx="17.5" cy="10.5" r=".5"/><circle cx="8.5" cy="7.5" r=".5"/><circle cx="6.5" cy="12.5" r=".5"/><path d="M12 2a10 10 0 0 0 0 20c.93 0 1.5-.75 1.5-1.5 0-.39-.15-.74-.39-1.01-.24-.27-.38-.62-.38-1 0-.83.67-1.5 1.5-1.5H16a6 6 0 0 0 6-6c0-5.52-4.48-9-10-9Z"/>',
    flecha: '<path d="M5 12h14M13 6l6 6-6 6"/>',
    deshacer: '<path d="M3 7v6h6"/><path d="M21 17a9 9 0 0 0-15-6.7L3 13"/>',
    guardar: '<path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><path d="M17 21v-8H7v8M7 3v5h8"/>',
    grafico: '<path d="M3 3v18h18"/><path d="m7 15 4-4 3 3 5-6"/>',
    estrella: '<path d="m12 2 3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/>',
    logo: '<path d="M22 10 12 5 2 10l10 5 10-5z"/><path d="M6 12v5c3 3 9 3 12 0v-5"/>',
    descargar: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m7 10 5 5 5-5"/><path d="M12 15V3"/>',
    subir: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="m17 8-5-5-5 5"/><path d="M12 3v12"/>',
    excel: '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M3 9h18M3 15h18M9 3v18"/>',
    celular: '<rect x="5" y="2" width="14" height="20" rx="2"/><path d="M12 18h.01"/>',
    escudo: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
    asistencia: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/><path d="m9 16 2 2 4-4"/>',
    atras: '<path d="M19 12H5M11 18l-6-6 6-6"/>',
    buzon: '<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/>',
    carpeta: '<path d="M4 20h16a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.93a2 2 0 0 1-1.66-.9l-.82-1.2A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>',
    corona: '<path d="m2 6 4 11h12l4-11-6 5-4-7-4 7-6-5Z"/><path d="M6 21h12"/>',
};

export function icono(nombre, clase = 'icono') {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '1.8');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('class', clase);
    svg.innerHTML = ICONOS[nombre] || ''; // trazos fijos definidos arriba
    return svg;
}

// ======================= Botones y piezas pequeñas =======================

/** variante: primario | secundario | peligro | fantasma */
export function boton(texto, alHacerClic, { variante = 'secundario', tipo = 'button', iconoNombre, titulo } = {}) {
    return el('button', { type: tipo, class: `boton boton--${variante}`, onclick: alHacerClic, title: titulo },
        iconoNombre && icono(iconoNombre), el('span', {}, texto));
}

export function botonIcono(nombreIcono, etiqueta, alHacerClic, variante = '') {
    return el('button', {
        type: 'button', class: `boton-icono ${variante ? 'boton-icono--' + variante : ''}`,
        'aria-label': etiqueta, title: etiqueta, onclick: alHacerClic,
    }, icono(nombreIcono));
}

/** tipo: neutro | primario | exito | peligro | aviso */
export function chip(texto, tipo = 'neutro') {
    return el('span', { class: `chip chip--${tipo}` }, texto);
}

export function avatar(nombre, clase = '') {
    return el('span', { class: `avatar ${clase}`, 'aria-hidden': 'true' }, iniciales(nombre));
}

/** Celda con avatar, nombre y una línea secundaria. */
export function persona(nombre, detalle) {
    return el('div', { class: 'persona' }, avatar(nombre),
        el('div', { class: 'persona__textos' }, el('span', { class: 'persona__nombre' }, nombre),
            detalle && el('span', { class: 'persona__detalle' }, detalle)));
}

export function tarjetaDato({ iconoNombre, titulo, valor, detalle }) {
    return el('div', { class: 'dato' },
        el('div', { class: 'dato__icono' }, icono(iconoNombre)),
        el('div', { class: 'dato__textos' },
            el('span', { class: 'dato__titulo' }, titulo),
            el('span', { class: 'dato__valor' }, String(valor)),
            detalle && el('span', { class: 'dato__detalle' }, detalle)));
}

export function barraAvance(valor, total) {
    const porcentaje = total ? Math.round((valor / total) * 100) : 0;
    return el('div', { class: 'avance', title: `${porcentaje}%` },
        el('div', { class: 'avance__pista' },
            el('div', { class: 'avance__relleno', style: `width: ${porcentaje}%` })),
        el('span', { class: 'avance__texto' }, `${valor} de ${total}`));
}

export function estadoVacio({ iconoNombre = 'info', titulo, texto, accion }) {
    return el('div', { class: 'vacio' },
        el('div', { class: 'vacio__icono' }, icono(iconoNombre)),
        el('h3', { class: 'vacio__titulo' }, titulo),
        texto && el('p', { class: 'vacio__texto' }, texto),
        accion);
}

export function panel({ titulo, descripcion, acciones, contenido, clase = '' }) {
    return el('section', { class: `panel ${clase}` },
        (titulo || acciones) && el('div', { class: 'panel__cabecera' },
            el('div', {}, titulo && el('h2', { class: 'panel__titulo' }, titulo),
                descripcion && el('p', { class: 'panel__descripcion' }, descripcion)),
            acciones && el('div', { class: 'panel__acciones' }, acciones)),
        contenido);
}

// ======================= Avisos =======================

export function avisar(texto, tipo = 'info') {
    const nombreIcono = { exito: 'check', error: 'alerta' }[tipo] || 'info';
    const aviso = el('div', { class: `aviso aviso--${tipo}`, role: tipo === 'error' ? 'alert' : 'status' },
        icono(nombreIcono, 'icono aviso__icono'),
        el('span', { class: 'aviso__texto' }, texto),
        el('button', { type: 'button', class: 'aviso__cerrar', 'aria-label': 'Cerrar', onclick: () => cerrar() },
            icono('cerrar')));
    $('#avisos').append(aviso);
    const temporizador = setTimeout(cerrar, tipo === 'error' ? 7000 : 3500);

    function cerrar() {
        clearTimeout(temporizador);
        if (!aviso.isConnected || aviso.classList.contains('saliendo')) return;
        aviso.classList.add('saliendo');
        setTimeout(() => aviso.remove(), 220);
    }
}

// ======================= Ventanas modales =======================

const modal = () => $('#modal');

function abrirModal(contenido) {
    const dialogo = modal();
    dialogo.replaceChildren(contenido);
    if (!dialogo.open) dialogo.showModal();
    contenido.querySelector('input:not([disabled]):not([type=hidden]):not([type=checkbox]), select:not([disabled]), textarea:not([disabled])')?.focus();
}

export function cerrarModal() {
    const dialogo = modal();
    if (!dialogo.open || dialogo.classList.contains('cerrando')) return;
    dialogo.classList.add('cerrando');
    setTimeout(() => {
        dialogo.classList.remove('cerrando');
        dialogo.close();
    }, 140);
}

/** Pregunta de confirmación. Devuelve una promesa con true/false. */
export function confirmar({ titulo, mensaje, textoBoton = 'Confirmar', peligro = false }) {
    return new Promise((resolver) => {
        let respuesta = false;
        const terminar = (valor) => {
            respuesta = valor;
            cerrarModal();
        };
        modal().addEventListener('close', () => resolver(respuesta), { once: true });
        abrirModal(el('div', { class: 'modal__caja modal__caja--chica' },
            el('div', { class: 'modal__cuerpo modal__cuerpo--confirmar' },
                el('div', { class: `modal__icono ${peligro ? 'modal__icono--peligro' : ''}` },
                    icono(peligro ? 'alerta' : 'info')),
                el('div', {}, el('h2', { class: 'modal__titulo' }, titulo),
                    el('p', { class: 'modal__texto' }, mensaje))),
            el('div', { class: 'modal__pie' },
                boton('Cancelar', () => terminar(false)),
                boton(textoBoton, () => terminar(true), { variante: peligro ? 'peligro' : 'primario' }))));
    });
}

/**
 * Formulario en ventana modal. Cada campo: { nombre, etiqueta, tipo, opciones: [{valor, texto}],
 * requerido, bloqueado, ayuda, placeholder, min, max, minlength, maxlength, ancho: 'completo' }.
 * tipo 'textarea' = texto de varias líneas; tipo 'checkbox' = casilla (en los datos llega 'on' si está marcada).
 * alGuardar(datos) puede lanzar un error: se muestra dentro de la ventana sin cerrarla.
 */
export function abrirFormulario({ titulo, descripcion, antes, campos, valores = {}, textoBoton = 'Guardar', alGuardar }) {
    const error = el('div', { class: 'modal__error', role: 'alert', hidden: true });
    const botonGuardar = boton(textoBoton, null, { variante: 'primario', tipo: 'submit' });
    const formulario = el('form', { class: 'modal__caja' },
        el('div', { class: 'modal__cabecera' },
            el('div', {}, el('h2', { class: 'modal__titulo' }, titulo),
                descripcion && el('p', { class: 'modal__texto' }, descripcion)),
            botonIcono('cerrar', 'Cerrar', cerrarModal)),
        el('div', { class: 'modal__cuerpo formulario' },
            antes && el('div', { class: 'campo--completo' }, antes),
            campos.map((campo) => crearCampo(campo, valores[campo.nombre]))),
        error,
        el('div', { class: 'modal__pie' }, boton('Cancelar', cerrarModal), botonGuardar));

    formulario.addEventListener('submit', async (evento) => {
        evento.preventDefault();
        error.hidden = true;
        botonGuardar.disabled = true;
        botonGuardar.classList.add('boton--ocupado');
        try {
            await alGuardar(Object.fromEntries(new FormData(formulario)));
            cerrarModal();
        } catch (excepcion) {
            error.replaceChildren(icono('alerta'), el('span', {}, excepcion.message));
            error.hidden = false;
        } finally {
            botonGuardar.disabled = false;
            botonGuardar.classList.remove('boton--ocupado');
        }
    });
    abrirModal(formulario);
}

function crearCampo(campo, valor) {
    const { nombre, etiqueta, tipo = 'text', opciones = [], requerido = true, bloqueado, ayuda, ancho } = campo;
    let control;
    if (tipo === 'checkbox') {
        control = el('input', { name: nombre, type: 'checkbox', disabled: bloqueado });
        control.checked = Boolean(valor);
        return el('label', { class: `campo campo--casilla ${ancho === 'completo' ? 'campo--completo' : ''}` },
            control,
            el('span', { class: 'campo__textos' }, el('span', { class: 'campo__etiqueta' }, etiqueta),
                ayuda && el('small', { class: 'campo__ayuda' }, ayuda)));
    }
    if (tipo === 'textarea') {
        control = el('textarea', { name: nombre, required: requerido, disabled: bloqueado, rows: campo.filas ?? 3,
            placeholder: campo.placeholder, maxlength: campo.maxlength });
    } else if (tipo === 'select') {
        control = el('select', { name: nombre, required: requerido, disabled: bloqueado },
            opciones.map((opcion) => el('option', { value: opcion.valor }, opcion.texto)));
    } else {
        control = el('input', {
            name: nombre, type: tipo, required: requerido, disabled: bloqueado,
            placeholder: campo.placeholder, min: campo.min, max: campo.max,
            minlength: campo.minlength, maxlength: campo.maxlength, pattern: campo.pattern,
            inputmode: campo.inputmode, class: campo.clase,
            autocomplete: campo.autocomplete ?? (tipo === 'password' ? 'new-password' : 'off'),
        });
    }
    if (valor !== undefined && valor !== null) control.value = valor;
    return el('label', { class: `campo ${ancho === 'completo' ? 'campo--completo' : ''}` },
        el('span', { class: 'campo__etiqueta' }, etiqueta, !requerido && el('span', { class: 'campo__opcional' }, ' (opcional)')),
        control,
        ayuda && el('small', { class: 'campo__ayuda' }, ayuda));
}

// ======================= Tablas =======================

/**
 * columnas: [{ titulo, celda: (fila) => texto|nodo, clase }]
 * Cada fila guarda su texto normalizado para la búsqueda rápida (ver buscador).
 */
export function tabla({ columnas, filas, vacio }) {
    const cuerpo = el('tbody', {}, filas.map((fila) => {
        const tr = el('tr', {}, columnas.map((columna) => el('td', { class: columna.clase }, columna.celda(fila))));
        tr.dataset.busqueda = normalizar(tr.textContent);
        return tr;
    }));
    const sinResultados = el('tr', { class: 'tabla__sin-resultados', hidden: true },
        el('td', { colspan: columnas.length }, 'No hay resultados para esa búsqueda.'));
    cuerpo.append(sinResultados);

    if (!filas.length) {
        return el('div', { class: 'tabla-contenedor tabla-contenedor--vacia' }, vacio);
    }
    return el('div', { class: 'tabla-contenedor' },
        el('table', { class: 'tabla' },
            el('thead', {}, el('tr', {}, columnas.map((columna) => el('th', { class: columna.clase }, columna.titulo)))),
            cuerpo));
}

/** Campo de búsqueda que filtra en vivo las filas de las tablas dentro de `ambito`. */
export function buscador(placeholder, ambito, valorInicial = '', alCambiar = () => {}) {
    const entrada = el('input', { type: 'search', class: 'buscador__entrada', placeholder, 'aria-label': placeholder });
    entrada.value = valorInicial;
    const aplicar = () => {
        const texto = normalizar(entrada.value.trim());
        for (const cuerpo of ambito().querySelectorAll('tbody')) {
            let visibles = 0;
            for (const fila of cuerpo.querySelectorAll('tr[data-busqueda]')) {
                fila.hidden = Boolean(texto) && !fila.dataset.busqueda.includes(texto);
                if (!fila.hidden) visibles++;
            }
            const aviso = cuerpo.querySelector('.tabla__sin-resultados');
            if (aviso) aviso.hidden = visibles > 0;
        }
        alCambiar(entrada.value);
    };
    entrada.addEventListener('input', aplicar);
    queueMicrotask(aplicar);
    return el('label', { class: 'buscador' }, icono('buscar'), entrada);
}

export function selectorFiltro(etiqueta, opciones, valor, alCambiar) {
    const select = el('select', { class: 'filtro__select', 'aria-label': etiqueta, onchange: (e) => alCambiar(e.target.value) },
        opciones.map((opcion) => el('option', { value: opcion.valor }, opcion.texto)));
    select.value = valor ?? '';
    return el('label', { class: 'filtro' }, el('span', { class: 'filtro__etiqueta' }, etiqueta), select);
}

export function barraHerramientas(...elementos) {
    return el('div', { class: 'herramientas' }, elementos);
}

// ======================= Carga =======================

export function esqueleto() {
    const linea = (ancho) => el('div', { class: 'esqueleto', style: `width: ${ancho}` });
    return el('div', { class: 'pila', 'aria-busy': 'true', 'aria-label': 'Cargando' },
        el('div', { class: 'tarjetas' }, [1, 2, 3, 4].map(() => el('div', { class: 'dato dato--esqueleto' }, linea('40%'), linea('60%')))),
        el('div', { class: 'panel' }, [70, 90, 80, 95, 60].map((ancho) => el('div', { class: 'esqueleto-fila' }, linea(`${ancho}%`)))));
}
