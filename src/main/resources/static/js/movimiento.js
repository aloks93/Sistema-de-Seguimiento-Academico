/**
 * Movimiento de la interfaz:
 *  - Al entrar a una pantalla, lo visible aparece en cascada (desenfoque → nítido, de abajo hacia arriba).
 *  - Lo que está más abajo se va acercando y enfocando a medida que se hace scroll; en navegadores con
 *    animaciones ligadas al scroll (Chrome / Edge) el movimiento sigue exactamente al scroll.
 *  - Las filas de las tablas aparecen al llegar a ellas.
 *  - Scroll con inercia: la rueda del mouse no salta de a 100 px, se desliza.
 *  - El encabezado se compacta y se vuelve vidrio al bajar.
 *  - Tarjetas que se inclinan en 3D siguiendo el mouse, con un brillo que sigue al cursor.
 *  - Transición suave entre pantallas (View Transitions) y titular del login que aparece palabra por palabra.
 * Todo se desactiva si el sistema pide menos movimiento.
 */

const reducido = window.matchMedia('(prefers-reduced-motion: reduce)');
export const sinMovimiento = () => reducido.matches;

const scrollNativo = CSS.supports?.('animation-timeline: view()') ?? false;
const MAXIMO_FILAS = 80;

let observador = null;

function contenedorScroll() {
    return document.getElementById('principal');
}

/** Se marca "visto" una sola vez: dispara la aparición (si hace falta), las barras y los contadores. */
function marcarVisto(nodo) {
    if (nodo.classList.contains('visto')) return;
    nodo.classList.add('visto');
    nodo.querySelectorAll('.dato__valor').forEach(contarHasta);
    // Terminada la aparición se quita el estado inicial (así la tarjeta puede volver a inclinarse)
    if (nodo.classList.contains('oculto')) setTimeout(() => nodo.classList.remove('oculto'), 1500);
}

function observar(nodos) {
    observador?.disconnect();
    observador = new IntersectionObserver((entradas) => {
        // Los que entran juntos se escalonan entre sí
        let orden = 0;
        for (const entrada of entradas) {
            if (!entrada.isIntersecting) continue;
            const nodo = entrada.target;
            nodo.style.setProperty('--retraso', `${Math.min(orden++, 8) * 70}ms`);
            marcarVisto(nodo);
            observador.unobserve(nodo);
        }
    }, { root: contenedorScroll(), rootMargin: '0px 0px -8% 0px', threshold: 0.08 });
    nodos.forEach((nodo) => observador.observe(nodo));
}

/** Prepara la entrada de una pantalla recién dibujada. */
export function revelar(contenido) {
    if (sinMovimiento()) return;
    const raiz = contenido.firstElementChild;
    if (!raiz) return;
    const alto = (contenedorScroll() ?? document.documentElement).clientHeight;

    const bloques = raiz.classList.contains('pila')
        ? [...raiz.children].filter((hijo) => !hijo.classList.contains('barra-guardar'))
        : [raiz];
    const tarjetas = [...contenido.querySelectorAll('.tarjetas > *, .tarjetas-materia > *')];
    const filas = [...contenido.querySelectorAll('.tabla tbody tr')].slice(0, MAXIMO_FILAS);

    const pendientes = [];
    let enPantalla = 0;
    const preparar = (nodo, { arrastrable }) => {
        const caja = nodo.getBoundingClientRect();
        if (caja.top < alto * 0.92) {
            // Visible al entrar: cascada por tiempo
            nodo.classList.add('intro');
            nodo.style.setProperty('--i', Math.min(enPantalla++, 10));
            nodo.addEventListener('animationend', function fin(evento) {
                if (evento.target !== nodo) return;
                nodo.classList.remove('intro');
                nodo.removeEventListener('animationend', fin);
            });
            setTimeout(() => marcarVisto(nodo), 120 + Math.min(enPantalla, 10) * 70);
        } else if (arrastrable && scrollNativo) {
            // Más abajo: el scroll lo acerca (animación ligada al scroll del navegador)
            nodo.classList.add('arrastre');
            pendientes.push(nodo);
        } else {
            nodo.classList.add('oculto');
            pendientes.push(nodo);
        }
    };
    bloques.forEach((nodo) => preparar(nodo, { arrastrable: true }));
    tarjetas.forEach((nodo) => preparar(nodo, { arrastrable: false }));
    filas.forEach((nodo) => preparar(nodo, { arrastrable: false }));
    observar(pendientes);
}

/** Cambia el contenido con una transición suave entre pantallas si el navegador la tiene. */
export function transicion(cambiar) {
    if (sinMovimiento() || !document.startViewTransition) {
        cambiar();
        return Promise.resolve();
    }
    return document.startViewTransition(cambiar).updateCallbackDone;
}

/** Anima un número de 0 a su valor ("8,5", "72 %", "12"...). Los textos que no son números quedan igual. */
export function contarHasta(nodo) {
    if (nodo.dataset.contado) return;
    nodo.dataset.contado = '1';
    const texto = nodo.textContent.trim();
    const partes = /^(-?\d+(?:[.,]\d+)?)(\s*%?)$/.exec(texto);
    if (!partes || sinMovimiento()) return;
    const decimales = (partes[1].split(/[.,]/)[1] ?? '').length;
    const separador = partes[1].includes(',') ? ',' : '.';
    const destino = Number(partes[1].replace(',', '.'));
    if (!Number.isFinite(destino) || destino === 0) return;
    const duracion = 1200;
    const inicio = performance.now();
    const paso = (ahora) => {
        const avance = Math.min((ahora - inicio) / duracion, 1);
        const suavizado = 1 - Math.pow(1 - avance, 4);
        nodo.textContent = (destino * suavizado).toFixed(decimales).replace('.', separador) + partes[2];
        if (avance < 1) requestAnimationFrame(paso);
        else nodo.textContent = texto;
    };
    requestAnimationFrame(paso);
}

/** Reinicia una animación de un solo uso (latido, destello, sacudida). */
export function destacar(nodo, clase) {
    if (!nodo || sinMovimiento()) return;
    nodo.classList.remove(clase);
    void nodo.offsetWidth;
    nodo.classList.add(clase);
    nodo.addEventListener('animationend', () => nodo.classList.remove(clase), { once: true });
}

/** Divide un texto en palabras que aparecen una tras otra. */
export function titularPorPalabras(nodo) {
    if (!nodo || nodo.dataset.dividido) return;
    nodo.dataset.dividido = '1';
    const palabras = nodo.textContent.trim().split(/\s+/);
    nodo.setAttribute('aria-label', nodo.textContent.trim());
    nodo.replaceChildren(...palabras.flatMap((palabra, i) => {
        const span = document.createElement('span');
        span.className = 'palabra';
        span.setAttribute('aria-hidden', 'true');
        span.style.setProperty('--i', i);
        span.textContent = palabra;
        return i < palabras.length - 1 ? [span, document.createTextNode(' ')] : [span];
    }));
}

const SELECTOR_INCLINABLE = '.dato, .tarjetas-materia > *, .login__formulario';

/**
 * Scroll con inercia para la rueda del mouse: en lugar de saltar, el contenido se desliza hasta su destino.
 * No toca el teclado, la barra de desplazamiento ni los paneles que tienen scroll propio.
 */
function scrollConInercia(contenedor) {
    let destino = 0;
    let posicion = 0;
    let animando = false;
    let anterior = 0;

    const tieneScrollPropio = (nodo) => {
        for (let actual = nodo; actual && actual !== contenedor; actual = actual.parentElement) {
            if (actual.scrollHeight > actual.clientHeight + 1 && /(auto|scroll)/.test(getComputedStyle(actual).overflowY)) {
                return true;
            }
        }
        return false;
    };

    contenedor.addEventListener('wheel', (evento) => {
        if (sinMovimiento() || evento.ctrlKey || Math.abs(evento.deltaX) > Math.abs(evento.deltaY)) return;
        if (tieneScrollPropio(evento.target)) return;
        evento.preventDefault();
        const factor = evento.deltaMode === 1 ? 40 : evento.deltaMode === 2 ? contenedor.clientHeight : 1;
        if (!animando) {
            posicion = contenedor.scrollTop;
            destino = posicion;
        }
        const maximo = contenedor.scrollHeight - contenedor.clientHeight;
        destino = Math.max(0, Math.min(destino + evento.deltaY * factor, maximo));
        if (!animando) {
            animando = true;
            anterior = performance.now();
            requestAnimationFrame(paso);
        }
    }, { passive: false });

    function paso(ahora) {
        // Si el usuario movió la barra o usó el teclado mientras tanto, se le deja el control
        const desvio = Math.abs(contenedor.scrollTop - posicion);
        if (desvio > 80) {
            animando = false;
            return;
        }
        if (desvio > 2) posicion = contenedor.scrollTop;
        const dt = Math.min(ahora - anterior, 64);
        anterior = ahora;
        const suavizado = 1 - Math.pow(1 - 0.16, dt / 16.67);
        posicion += (destino - posicion) * suavizado;
        if (Math.abs(destino - posicion) < 0.5) posicion = destino;
        contenedor.scrollTop = posicion;
        if (posicion !== destino) requestAnimationFrame(paso);
        else animando = false;
    }
}

/** Efectos globales que se instalan una sola vez. */
export function iniciarMovimiento() {
    const principal = contenedorScroll();
    if (principal) scrollConInercia(principal);

    // Encabezado que se compacta al bajar
    const encabezado = document.querySelector('.encabezado');
    if (principal && encabezado) {
        let pendiente = false;
        principal.addEventListener('scroll', () => {
            if (pendiente) return;
            pendiente = true;
            requestAnimationFrame(() => {
                pendiente = false;
                encabezado.classList.toggle('compacto', principal.scrollTop > 24);
            });
        }, { passive: true });
    }

    // Inclinación 3D y brillo que sigue al cursor
    let activo = null;
    document.addEventListener('pointermove', (evento) => {
        if (evento.pointerType !== 'mouse' || sinMovimiento()) return;
        const objetivo = evento.target.closest?.(SELECTOR_INCLINABLE);
        if (activo && activo !== objetivo) soltar(activo);
        if (!objetivo) return;
        activo = objetivo;
        const caja = objetivo.getBoundingClientRect();
        const x = (evento.clientX - caja.left) / caja.width;
        const y = (evento.clientY - caja.top) / caja.height;
        const fuerza = objetivo.classList.contains('login__formulario') ? 3 : 7;
        objetivo.classList.add('inclinado');
        objetivo.style.setProperty('--rx', `${((0.5 - y) * fuerza).toFixed(2)}deg`);
        objetivo.style.setProperty('--ry', `${((x - 0.5) * fuerza).toFixed(2)}deg`);
        objetivo.style.setProperty('--mx', `${(x * 100).toFixed(1)}%`);
        objetivo.style.setProperty('--my', `${(y * 100).toFixed(1)}%`);
    }, { passive: true });
    document.addEventListener('pointerleave', () => activo && soltar(activo));
    function soltar(nodo) {
        nodo.classList.remove('inclinado');
        nodo.style.removeProperty('--rx');
        nodo.style.removeProperty('--ry');
        activo = null;
    }

    // Login: el fondo sigue suavemente al mouse
    const marca = document.querySelector('.login__marca');
    if (marca) {
        marca.addEventListener('pointermove', (evento) => {
            if (sinMovimiento()) return;
            const caja = marca.getBoundingClientRect();
            marca.style.setProperty('--px', ((evento.clientX - caja.left) / caja.width - 0.5).toFixed(3));
            marca.style.setProperty('--py', ((evento.clientY - caja.top) / caja.height - 0.5).toFixed(3));
        }, { passive: true });
    }
    titularPorPalabras(document.querySelector('.login__titular'));
}
