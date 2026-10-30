# Abre la página web en el navegador si el backend está corriendo.
# Uso: comandos\abrir-pagina-web.cmd

. "$PSScriptRoot\comun.ps1"

Escribir-Titulo "Abrir la PÁGINA WEB"
if (-not (Backend-Corriendo)) {
    Escribir-Error "El backend no está corriendo, así que no hay página para abrir."
    Escribir-Info  "Arránquelo con iniciar-todo.cmd o comandos\iniciar-backend.cmd."
    exit 1
}
Start-Process $UrlApp
Escribir-Ok "Abriendo $UrlApp en el navegador."
exit 0
