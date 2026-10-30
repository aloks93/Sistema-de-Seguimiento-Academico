# Verifica requisitos, levanta la base de datos, arranca el backend y abre la página web.
# Uso: iniciar-todo.cmd

. "$PSScriptRoot\comun.ps1"
Set-Location $Raiz

Escribir-Titulo "Iniciar TODO: base de datos + backend + página web"

& (Join-Path $PSScriptRoot 'verificar-requisitos.ps1')
if ($LASTEXITCODE -ne 0) {
    Escribir-Error "No se puede iniciar: primero instalar o corregir lo que falta (ver la lista de arriba)."
    exit 1
}

Escribir-Titulo "Paso 1 de 2: base de datos"
if (-not (Iniciar-BaseDeDatos)) { exit 1 }

Escribir-Titulo "Paso 2 de 2: backend"
if (Backend-Corriendo) {
    Escribir-Aviso "El backend ya está corriendo; se abre la página web."
    Start-Process $UrlApp
    exit 0
}
Abrir-NavegadorCuandoEsteListo
Escribir-Info "Cuando el backend esté listo se abrirá solo el navegador."
Mostrar-InstruccionesBackend
& (Join-Path $Raiz 'mvnw.cmd') spring-boot:run
exit $LASTEXITCODE
