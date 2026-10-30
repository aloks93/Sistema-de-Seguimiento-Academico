# Arranca solo el backend (API REST + página web). La base de datos tiene que estar encendida.
# Uso: comandos\iniciar-backend.cmd

. "$PSScriptRoot\comun.ps1"
Set-Location $Raiz

Escribir-Titulo "Iniciar solo el BACKEND (API + página web en $UrlApp)"

if (Backend-Corriendo) {
    Escribir-Aviso "El backend ya está corriendo en $UrlApp (ver comandos\estado.cmd)."
    exit 0
}
if (-not (Java-Correcto)) {
    Escribir-Error "Falta Java $JavaRequerido. Ejecute comandos\verificar-requisitos.cmd para ver cómo instalarlo."
    exit 1
}
Escribir-Ok "Java $JavaRequerido"

if (-not (Puerto-EnUso $PuertoBase)) {
    Escribir-Error "La base de datos está apagada (nada escucha en el puerto $PuertoBase)."
    Escribir-Info  "Enciéndala con comandos\iniciar-base-de-datos.cmd, o use iniciar-todo.cmd."
    exit 1
}
if (Docker-Abierto) {
    $ajeno = Contenedor-AjenoEnPuertoBase
    if ($ajeno) {
        Escribir-Error "El puerto $PuertoBase lo usa el contenedor '$ajeno' de otro proyecto, no la base de este."
        Escribir-Info  "Deténgalo con:  docker stop $ajeno   y luego ejecute comandos\iniciar-base-de-datos.cmd"
        exit 1
    }
}
Escribir-Ok "Base de datos encendida en el puerto $PuertoBase"

Mostrar-InstruccionesBackend
& (Join-Path $Raiz 'mvnw.cmd') spring-boot:run
exit $LASTEXITCODE
