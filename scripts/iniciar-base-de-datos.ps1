# Levanta solo la base de datos PostgreSQL en Docker.
# Uso: comandos\iniciar-base-de-datos.cmd

. "$PSScriptRoot\comun.ps1"

Escribir-Titulo "Iniciar solo la BASE DE DATOS (PostgreSQL en Docker)"
if (-not (Iniciar-BaseDeDatos)) { exit 1 }

Write-Host ""
Escribir-Info "Datos de conexión:  localhost:$PuertoBase  base 'seguimiento_academico'  usuario 'upe'  contraseña 'upe'"
Escribir-Info "Siguiente paso:     comandos\iniciar-backend.cmd"
exit 0
