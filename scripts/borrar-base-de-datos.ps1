# Borra la base de datos por completo (contenedor y volumen). Al volver a iniciar
# se crea vacía, con los datos de ejemplo.
# Uso: comandos\borrar-base-de-datos.cmd

. "$PSScriptRoot\comun.ps1"

Escribir-Titulo "BORRAR la base de datos"

if (Backend-Corriendo) {
    Escribir-Error "Primero detenga el backend (comandos\detener-backend.cmd)."
    exit 1
}
if (-not (Docker-Abierto)) {
    Escribir-Error "Docker Desktop tiene que estar abierto para borrar la base."
    exit 1
}

Write-Host "  Se van a ELIMINAR todos los datos cargados: carreras, docentes, materias," -ForegroundColor Red
Write-Host "  estudiantes y notas. Esto NO se puede deshacer." -ForegroundColor Red
Write-Host ""
$respuesta = Read-Host "  Escriba BORRAR para confirmar (cualquier otra cosa cancela)"
if ($respuesta -cne 'BORRAR') {
    Escribir-Ok "Cancelado. No se borró nada."
    exit 0
}

Push-Location $Raiz
try {
    & docker compose down -v | Out-Host
    $ok = ($LASTEXITCODE -eq 0)
} finally {
    Pop-Location
}
if (-not $ok) {
    Escribir-Error "No se pudo borrar la base de datos (ver mensajes de arriba)."
    exit 1
}
Escribir-Ok "Base de datos borrada. Al iniciar de nuevo se crea vacía con los datos de ejemplo."
exit 0
