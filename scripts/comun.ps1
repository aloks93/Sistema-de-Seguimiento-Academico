# Funciones compartidas por los scripts de la carpeta scripts\.
# Cada script las carga con:  . "$PSScriptRoot\comun.ps1"

$Raiz = Split-Path -Parent $PSScriptRoot
$UrlApp = 'http://localhost:8080'
$PuertoApp = 8080
$PuertoBase = 5432
$ContenedorBase = 'seguimiento-academico-db'
$JavaRequerido = 21

# ---------- Mensajes ----------

function Escribir-Titulo([string]$texto) {
    Write-Host ""
    Write-Host "=== $texto ===" -ForegroundColor Cyan
    Write-Host ""
}
function Escribir-Ok([string]$texto)    { Write-Host "  [OK]     $texto" -ForegroundColor Green }
function Escribir-Falta([string]$texto) { Write-Host "  [FALTA]  $texto" -ForegroundColor Red }
function Escribir-Error([string]$texto) { Write-Host "  [ERROR]  $texto" -ForegroundColor Red }
function Escribir-Aviso([string]$texto) { Write-Host "  [AVISO]  $texto" -ForegroundColor Yellow }
function Escribir-Info([string]$texto)  { Write-Host "  $texto" }

# ---------- Comprobaciones ----------

function Puerto-EnUso([int]$puerto) {
    return [bool](Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue)
}

function Docker-Instalado {
    return [bool](Get-Command docker -ErrorAction SilentlyContinue)
}

function Docker-Abierto {
    if (-not (Docker-Instalado)) { return $false }
    & docker info 2>&1 | Out-Null
    return ($LASTEXITCODE -eq 0)
}

function Obtener-VersionJava([string]$javaExe) {
    # java -version escribe en stderr, por eso se lanza el proceso a mano
    $psi = New-Object System.Diagnostics.ProcessStartInfo $javaExe, '-version'
    $psi.UseShellExecute = $false
    $psi.RedirectStandardError = $true
    $psi.RedirectStandardOutput = $true
    $psi.CreateNoWindow = $true
    try {
        $proceso = [System.Diagnostics.Process]::Start($psi)
        $salida = $proceso.StandardError.ReadToEnd() + $proceso.StandardOutput.ReadToEnd()
        $proceso.WaitForExit()
    } catch {
        return $null
    }
    if ($salida -match 'version "(\d+)(?:\.(\d+))?') {
        $mayor = [int]$Matches[1]
        if ($mayor -eq 1) { $mayor = [int]$Matches[2] }   # formato antiguo: "1.8" = Java 8
        return $mayor
    }
    return $null
}

# Java que usará Maven: el de JAVA_HOME si está definido; si no, el del PATH
function Obtener-JavaExe {
    if ($env:JAVA_HOME) {
        $exe = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path $exe) { return $exe }
        return $null
    }
    $enPath = Get-Command java -ErrorAction SilentlyContinue
    if ($enPath) { return $enPath.Source }
    return $null
}

function Java-Correcto {
    $exe = Obtener-JavaExe
    if (-not $exe) { return $false }
    $version = Obtener-VersionJava $exe
    return ($version -and $version -ge $JavaRequerido)
}

# ---------- Base de datos ----------

# 'corriendo', 'detenido' o 'no existe' (requiere Docker abierto)
function Estado-ContenedorBase {
    $estado = & docker ps -a --filter "name=^$ContenedorBase$" --format "{{.State}}" 2>$null
    if (-not $estado) { return 'no existe' }
    if ($estado -eq 'running') { return 'corriendo' }
    return 'detenido'
}

# Nombre de otro contenedor (de otro proyecto) que ocupe el puerto de la base, o $null
function Contenedor-AjenoEnPuertoBase {
    return & docker ps --filter "publish=$PuertoBase" --format "{{.Names}}" 2>$null |
        Where-Object { $_ -ne $ContenedorBase } | Select-Object -First 1
}

# Levanta PostgreSQL con Docker y espera a que esté listo. Devuelve $true si quedó disponible.
function Iniciar-BaseDeDatos {
    if (-not (Docker-Abierto)) {
        if (Puerto-EnUso $PuertoBase) {
            Escribir-Aviso "Docker está cerrado, pero hay un PostgreSQL en el puerto $PuertoBase; se usará ese."
            return $true
        }
        if (Docker-Instalado) {
            Escribir-Error "Docker Desktop está cerrado. Ábralo, espere a que diga 'Engine running' y vuelva a intentar."
        } else {
            Escribir-Error "Docker no está instalado. Ejecute comandos\verificar-requisitos.cmd para ver cómo instalarlo."
        }
        return $false
    }

    $ajeno = Contenedor-AjenoEnPuertoBase
    if ($ajeno) {
        Escribir-Error "El puerto $PuertoBase lo usa el contenedor '$ajeno' de otro proyecto."
        Escribir-Info  "Deténgalo con:  docker stop $ajeno   (se vuelve a encender con: docker start $ajeno)"
        return $false
    }
    if ((Puerto-EnUso $PuertoBase) -and (Estado-ContenedorBase) -ne 'corriendo') {
        Escribir-Aviso "Ya hay otro PostgreSQL instalado usando el puerto $PuertoBase; se usará ese."
        return $true
    }

    Escribir-Info "Levantando PostgreSQL en Docker (la primera vez descarga la imagen)..."
    Push-Location $Raiz
    try {
        & docker compose up -d --wait | Out-Host
        $ok = ($LASTEXITCODE -eq 0)
    } finally {
        Pop-Location
    }
    if ($ok) {
        Escribir-Ok "Base de datos lista en localhost:$PuertoBase"
    } else {
        Escribir-Error "No se pudo levantar la base de datos (ver mensajes de arriba)."
    }
    return $ok
}

function Detener-BaseDeDatos {
    if (-not (Docker-Abierto)) {
        Escribir-Aviso "Docker está cerrado: la base de datos ya está apagada."
        return
    }
    if ((Estado-ContenedorBase) -ne 'corriendo') {
        Escribir-Ok "La base de datos ya estaba detenida."
        return
    }
    Push-Location $Raiz
    try {
        & docker compose stop | Out-Host
    } finally {
        Pop-Location
    }
    Escribir-Ok "Base de datos detenida (los datos se conservan)."
}

# ---------- Backend ----------

function Backend-Corriendo {
    return (Puerto-EnUso $PuertoApp)
}

function Backend-Responde {
    try {
        return ((Invoke-WebRequest -UseBasicParsing -Uri "$UrlApp/index.html" -TimeoutSec 3).StatusCode -eq 200)
    } catch {
        return $false
    }
}

# Abre el navegador en un proceso aparte apenas el backend escuche (espera hasta 10 minutos)
function Abrir-NavegadorCuandoEsteListo {
    $comando = "for (`$i = 0; `$i -lt 600; `$i++) { " +
        "if (Get-NetTCPConnection -LocalPort $PuertoApp -State Listen -ErrorAction SilentlyContinue) { " +
        "Start-Process '$UrlApp'; break }; Start-Sleep 1 }"
    Start-Process powershell -WindowStyle Hidden -ArgumentList '-NoProfile', '-Command', $comando
}

function Mostrar-InstruccionesBackend {
    Write-Host ""
    Escribir-Info "Arrancando el backend (la primera vez tarda varios minutos en descargar librerías)..."
    Escribir-Info "Está listo cuando aparece:  Started SeguimientoAcademicoApplication"
    Escribir-Info "Página web y API:           $UrlApp"
    Write-Host "  NO cierre esta ventana mientras use el sistema. Para detenerlo: Ctrl + C" -ForegroundColor Yellow
    Write-Host ""
}

# Detiene el proceso Java que atiende el puerto del backend. No toca programas ajenos.
function Detener-Backend {
    $conexiones = Get-NetTCPConnection -LocalPort $PuertoApp -State Listen -ErrorAction SilentlyContinue
    if (-not $conexiones) {
        Escribir-Ok "El backend ya estaba detenido."
        return
    }
    foreach ($idProceso in ($conexiones.OwningProcess | Sort-Object -Unique)) {
        $proceso = Get-Process -Id $idProceso -ErrorAction SilentlyContinue
        if ($proceso -and $proceso.ProcessName -in 'java', 'javaw') {
            Stop-Process -Id $idProceso -Force
            Escribir-Ok "Backend detenido."
        } elseif ($proceso) {
            Escribir-Aviso "El puerto $PuertoApp lo usa otro programa ($($proceso.ProcessName)); no se detuvo."
        }
    }
}
