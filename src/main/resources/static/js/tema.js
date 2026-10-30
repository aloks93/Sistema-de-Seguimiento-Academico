// Apariencia: aplica las preferencias de cada usuario (modo, colores, densidad y tamaño de texto).
// Las preferencias se guardan en el servidor (/api/perfil/preferencias) y se recuerdan en el navegador
// para aplicarlas al instante al recargar, antes de que responda el servidor.

export const TEMA_POR_DEFECTO = Object.freeze({
    modo: 'oscuro',
    colorPrimario: '#2563eb',
    colorBarra: '#05070b',
    densidad: 'comoda',
    tamanoTexto: 'normal',
});

export const COLORES_PRIMARIOS = [
    ['Azul', '#2563eb'], ['Celeste', '#0284c7'], ['Índigo', '#4f46e5'], ['Violeta', '#7c3aed'],
    ['Turquesa', '#0d9488'], ['Verde', '#16a34a'], ['Naranja', '#ea580c'], ['Rojo', '#dc2626'], ['Rosa', '#db2777'],
];

export const COLORES_BARRA = [
    ['Negro', '#05070b'], ['Azul noche', '#0b1630'], ['Grafito', '#1f2937'],
    ['Azul marino', '#1e3a8a'], ['Gris claro', '#e5e7eb'], ['Blanco', '#ffffff'],
];

const sistemaClaro = window.matchMedia('(prefers-color-scheme: light)');
let actual = { ...TEMA_POR_DEFECTO };

sistemaClaro.addEventListener('change', () => {
    if (actual.modo === 'sistema') aplicarTema(actual);
});

function luminancia(hex) {
    const canal = (inicio) => {
        const valor = parseInt(hex.slice(inicio, inicio + 2), 16) / 255;
        return valor <= 0.03928 ? valor / 12.92 : ((valor + 0.055) / 1.055) ** 2.4;
    };
    return 0.2126 * canal(1) + 0.7152 * canal(3) + 0.0722 * canal(5);
}

/** Blanco o casi negro: el que más contraste tenga sobre ese color de fondo. */
function textoSobre(hex) {
    const fondo = luminancia(hex);
    const contrasteBlanco = 1.05 / (fondo + 0.05);
    const contrasteOscuro = (fondo + 0.05) / (luminancia('#0b1220') + 0.05);
    return contrasteBlanco >= contrasteOscuro ? '#ffffff' : '#0b1220';
}

export function aplicarTema(preferencias) {
    actual = { ...TEMA_POR_DEFECTO, ...preferencias };
    const raiz = document.documentElement;
    const modo = actual.modo === 'sistema' ? (sistemaClaro.matches ? 'claro' : 'oscuro') : actual.modo;
    raiz.dataset.modo = modo;
    raiz.dataset.densidad = actual.densidad;
    raiz.dataset.texto = actual.tamanoTexto;
    raiz.style.setProperty('--primario', actual.colorPrimario);
    raiz.style.setProperty('--sobre-primario', textoSobre(actual.colorPrimario));
    raiz.style.setProperty('--barra', actual.colorBarra);
    raiz.style.setProperty('--sobre-barra', textoSobre(actual.colorBarra) === '#ffffff' ? '#e8ecf4' : '#0b1220');
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', actual.colorBarra);
}

export function temaActual() {
    return { ...actual };
}

export function recordarTema(username, preferencias) {
    try {
        localStorage.setItem('tema:' + username, JSON.stringify(preferencias));
    } catch {
        // Sin almacenamiento: se usará el tema del servidor al cargar el perfil
    }
}

export function temaRecordado(username) {
    try {
        return JSON.parse(localStorage.getItem('tema:' + username));
    } catch {
        return null;
    }
}
