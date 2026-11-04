# Prueba rápida de la API con los usuarios de ejemplo del perfil dev.
# Requiere que el backend esté corriendo (iniciar-todo.cmd o comandos\iniciar-backend.cmd en otra ventana).
# Uso: comandos\probar-api.cmd

$api = 'http://localhost:8080'

function Iniciar-Sesion([string]$usuario, [string]$clave) {
    $cuerpo = @{ username = $usuario; password = $clave } | ConvertTo-Json
    $respuesta = Invoke-RestMethod -Method Post -Uri "$api/api/auth/login" -ContentType 'application/json' -Body $cuerpo
    Write-Host "  Sesión iniciada como '$($respuesta.username)' (rol $($respuesta.rol))" -ForegroundColor Green
    return @{ Authorization = "Bearer $($respuesta.token)" }
}

function Codigo-Http($errorRecord) {
    if ($errorRecord.Exception.Response) { return [int]$errorRecord.Exception.Response.StatusCode }
    return $null
}

try {
    Write-Host ""
    Write-Host "1. Login del docente" -ForegroundColor Cyan
    $docente = Iniciar-Sesion 'docente' 'docente123'

    Write-Host ""
    Write-Host "2. El docente registra una nota de 85 en INF-201 para Lucía (matrícula 2026-0001)" -ForegroundColor Cyan
    $nota = @{ estudianteId = 1; materiaId = 3; nota = 85 } | ConvertTo-Json
    try {
        $creada = Invoke-RestMethod -Method Post -Uri "$api/api/calificaciones" -Headers $docente -ContentType 'application/json' -Body $nota
        Write-Host "  Nota registrada con id $($creada.id)" -ForegroundColor Green
    } catch {
        if ((Codigo-Http $_) -eq 409) {
            Write-Host "  La nota ya existía de una prueba anterior (409 Conflict): es lo esperado" -ForegroundColor Yellow
        } else {
            throw
        }
    }

    Write-Host ""
    Write-Host "3. Login de la estudiante (lucia) y consulta de sus notas" -ForegroundColor Cyan
    $estudiante = Iniciar-Sesion 'lucia' 'estudiante123'
    # Invoke-RestMethod de PowerShell 5.1 no decodifica bien UTF-8 (tildes), por eso se lee a mano
    $respuesta = Invoke-WebRequest -UseBasicParsing -Uri "$api/api/estudiantes/me/calificaciones" -Headers $estudiante
    $json = [System.Text.Encoding]::UTF8.GetString($respuesta.RawContentStream.ToArray())
    $notas = $json | ConvertFrom-Json
    $notas | Format-Table matricula, nombreEstudiante, codigoMateria, nombreMateria, nota -AutoSize | Out-String | Write-Host

    Write-Host "4. El estudiante intenta registrar una nota (debe ser rechazado)" -ForegroundColor Cyan
    try {
        Invoke-RestMethod -Method Post -Uri "$api/api/calificaciones" -Headers $estudiante -ContentType 'application/json' -Body $nota | Out-Null
        Write-Host "  ERROR: el estudiante no debería poder registrar notas" -ForegroundColor Red
        exit 1
    } catch {
        if ((Codigo-Http $_) -ne 403) { throw }
        Write-Host "  Rechazado con 403 Forbidden: correcto" -ForegroundColor Green
    }

    Write-Host ""
    Write-Host "=== La API funciona correctamente ===" -ForegroundColor Green
    exit 0
} catch {
    Write-Host ""
    if (-not (Codigo-Http $_)) {
        Write-Host "No se pudo conectar con $api. ¿Está abierta la ventana del backend (iniciar-todo.cmd) y" -ForegroundColor Red
        Write-Host "ya apareció el mensaje 'Started SeguimientoAcademicoApplication'?" -ForegroundColor Red
    } else {
        Write-Host "Error inesperado: $($_.Exception.Message)" -ForegroundColor Red
    }
    exit 1
}
