# Muestra qué partes del sistema están encendidas.
# Uso: comandos\estado.cmd

. "$PSScriptRoot\comun.ps1"

Escribir-Titulo "Estado del sistema"

# Docker y base de datos
$dockerAbierto = Docker-Abierto
if (-not (Docker-Instalado)) {
    Escribir-Aviso "Docker ............ no instalado"
} elseif ($dockerAbierto) {
    Escribir-Ok    "Docker ............ abierto"
} else {
    Escribir-Aviso "Docker ............ cerrado (abrir Docker Desktop)"
}

if ($dockerAbierto) {
    $ajeno = Contenedor-AjenoEnPuertoBase
    switch (Estado-ContenedorBase) {
        'corriendo' {
            $salud = & docker inspect -f "{{.State.Health.Status}}" $ContenedorBase 2>$null
            if ($salud -eq 'healthy') { Escribir-Ok "Base de datos ..... encendida y lista (localhost:$PuertoBase)" }
            else { Escribir-Aviso "Base de datos ..... arrancando ($salud)" }
        }
        'detenido'  { Escribir-Aviso "Base de datos ..... apagada (comandos\iniciar-base-de-datos.cmd)" }
        default     { Escribir-Aviso "Base de datos ..... todavía no creada (se crea con comandos\iniciar-base-de-datos.cmd)" }
    }
    if ($ajeno) { Escribir-Error "Puerto $PuertoBase ........ ocupado por '$ajeno' de otro proyecto (docker stop $ajeno)" }
} elseif (Puerto-EnUso $PuertoBase) {
    Escribir-Ok "Base de datos ..... hay un PostgreSQL en el puerto $PuertoBase"
} else {
    Escribir-Aviso "Base de datos ..... apagada"
}

# Backend
if (Backend-Responde) {
    Escribir-Ok "Backend ........... encendido: $UrlApp"
} elseif (Backend-Corriendo) {
    Escribir-Aviso "Backend ........... el puerto $PuertoApp está ocupado pero no responde (¿arrancando, u otro programa?)"
} else {
    Escribir-Aviso "Backend ........... apagado (iniciar-todo.cmd o comandos\iniciar-backend.cmd)"
}
Write-Host ""
exit 0
