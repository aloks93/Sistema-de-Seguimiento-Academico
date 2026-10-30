# Menú con todas las acciones del proyecto.
# Uso: MENU.cmd

. "$PSScriptRoot\comun.ps1"
Set-Location $Raiz

$comandos = Join-Path $Raiz 'comandos'

# Las acciones que dejan algo corriendo se abren en una ventana aparte para que el menú siga disponible
function Abrir-EnVentanaNueva([string]$archivoCmd, [string]$descripcion) {
    Start-Process -FilePath $archivoCmd -WorkingDirectory $Raiz
    Write-Host ""
    Escribir-Ok "Se abrió una ventana nueva: $descripcion"
    Escribir-Info "No cierre esa ventana mientras use el sistema."
}

function Ejecutar([string]$script, [string[]]$argumentos = @()) {
    & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot $script) @argumentos
}

while ($true) {
    Clear-Host
    Write-Host ""
    Write-Host "  SISTEMA DE SEGUIMIENTO ACADÉMICO - MENÚ" -ForegroundColor Cyan
    Write-Host "  ---------------------------------------"
    $backend = if (Backend-Corriendo) { "encendido ($UrlApp)" } else { 'apagado' }
    $base = if (Puerto-EnUso $PuertoBase) { 'encendida' } else { 'apagada' }
    Write-Host "  Base de datos: $base    Backend: $backend" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "   INICIAR"
    Write-Host "    1. Iniciar TODO (base de datos + backend + página web)"
    Write-Host "    2. Iniciar solo la base de datos"
    Write-Host "    3. Iniciar solo el backend"
    Write-Host "    4. Abrir la página web"
    Write-Host ""
    Write-Host "   REVISAR"
    Write-Host "    5. Ver estado"
    Write-Host "    6. Verificar requisitos de la computadora"
    Write-Host "    7. Probar la API (con el backend encendido)"
    Write-Host "    8. Ejecutar los tests automáticos"
    Write-Host ""
    Write-Host "   DETENER"
    Write-Host "    9. Detener el backend"
    Write-Host "   10. Detener la base de datos"
    Write-Host "   11. Detener TODO"
    Write-Host ""
    Write-Host "   12. BORRAR la base de datos (empezar de cero)" -ForegroundColor Red
    Write-Host ""
    Write-Host "    0. Salir"
    Write-Host ""
    $opcion = (Read-Host "  Elija una opción").Trim()

    switch ($opcion) {
        '1'  { Abrir-EnVentanaNueva (Join-Path $Raiz 'iniciar-todo.cmd') 'base de datos + backend (el navegador se abre solo)' }
        '2'  { Ejecutar 'iniciar-base-de-datos.ps1' }
        '3'  { Abrir-EnVentanaNueva (Join-Path $comandos 'iniciar-backend.cmd') 'backend' }
        '4'  { Ejecutar 'abrir-pagina-web.ps1' }
        '5'  { Ejecutar 'estado.ps1' }
        '6'  { Ejecutar 'verificar-requisitos.ps1' }
        '7'  { Ejecutar 'probar-api.ps1' }
        '8'  { & (Join-Path $Raiz 'mvnw.cmd') test }
        '9'  { Ejecutar 'detener.ps1' '-Que', 'backend' }
        '10' { Ejecutar 'detener.ps1' '-Que', 'base' }
        '11' { Ejecutar 'detener.ps1' '-Que', 'todo' }
        '12' { Ejecutar 'borrar-base-de-datos.ps1' }
        '0'  { exit 0 }
        default { Escribir-Aviso "Opción no válida: '$opcion'" }
    }
    Write-Host ""
    Read-Host "  Presione Enter para volver al menú" | Out-Null
}
