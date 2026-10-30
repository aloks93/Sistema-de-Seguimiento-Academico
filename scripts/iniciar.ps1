# Verifica requisitos, levanta PostgreSQL con Docker y ejecuta la aplicación.
# Uso: doble clic en iniciar.cmd

$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

& (Join-Path $PSScriptRoot 'verificar-requisitos.ps1')
if ($LASTEXITCODE -ne 0) {
    Write-Host "No se puede iniciar: primero instalar lo que falta (ver la lista de arriba)." -ForegroundColor Red
    exit 1
}

# Base de datos: si el puerto 5432 ya lo usa otro PostgreSQL (no el contenedor del proyecto), se usa ese
$dockerAbierto = $false
if (Get-Command docker -ErrorAction SilentlyContinue) {
    & docker info 2>&1 | Out-Null
    $dockerAbierto = ($LASTEXITCODE -eq 0)
}

if ($dockerAbierto) {
    $contenedorPropio = & docker ps -q --filter "name=seguimiento-academico-db" 2>$null
    $puertoOcupado = [bool](Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue)
    if ($puertoOcupado -and -not $contenedorPropio) {
        Write-Host "Ya hay otro PostgreSQL usando el puerto 5432; se usará ese." -ForegroundColor Yellow
    } else {
        Write-Host ""
        Write-Host "Levantando la base de datos PostgreSQL con Docker..." -ForegroundColor Cyan
        & docker compose up -d --wait
        if ($LASTEXITCODE -ne 0) {
            Write-Host "No se pudo levantar la base de datos. Revisar que Docker Desktop esté abierto." -ForegroundColor Red
            exit 1
        }
    }
}

Write-Host ""
Write-Host "Iniciando la aplicación (la primera vez tarda varios minutos en descargar librerías)..." -ForegroundColor Cyan
Write-Host "Cuando aparezca 'Started SeguimientoAcademicoApplication', la API está en http://localhost:8080" -ForegroundColor Cyan
Write-Host "Para probarla: doble clic en probar-api.cmd. Para detenerla: Ctrl + C en esta ventana." -ForegroundColor Cyan
Write-Host ""
& (Join-Path $raiz 'mvnw.cmd') spring-boot:run
exit $LASTEXITCODE
