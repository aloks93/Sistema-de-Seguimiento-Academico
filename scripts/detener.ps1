# Detiene el backend, la base de datos o ambos.
# Uso: comandos\detener-todo.cmd | comandos\detener-backend.cmd | comandos\detener-base-de-datos.cmd

param(
    [ValidateSet('todo', 'backend', 'base')]
    [string]$Que = 'todo'
)

. "$PSScriptRoot\comun.ps1"

switch ($Que) {
    'backend' {
        Escribir-Titulo "Detener el BACKEND"
        Detener-Backend
    }
    'base' {
        Escribir-Titulo "Detener la BASE DE DATOS"
        if (Backend-Corriendo) {
            Escribir-Aviso "El backend sigue corriendo y va a dar errores sin base de datos."
        }
        Detener-BaseDeDatos
    }
    'todo' {
        Escribir-Titulo "Detener TODO (backend + base de datos)"
        Detener-Backend
        Detener-BaseDeDatos
    }
}
exit 0
