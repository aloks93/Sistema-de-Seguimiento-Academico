# Verifica que la computadora tenga todo lo necesario para ejecutar el proyecto
# y muestra una lista de lo que falta instalar.
# Uso: doble clic en verificar-requisitos.cmd
# Código de salida: 0 = todo listo, 1 = falta algo.

$JavaRequerido = 21
$faltantes = New-Object System.Collections.Generic.List[string]

function Escribir-Ok([string]$texto)    { Write-Host "  [OK]     $texto" -ForegroundColor Green }
function Escribir-Falta([string]$texto) { Write-Host "  [FALTA]  $texto" -ForegroundColor Red }
function Escribir-Aviso([string]$texto) { Write-Host "  [AVISO]  $texto" -ForegroundColor Yellow }

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

function Puerto-EnUso([int]$puerto) {
    return [bool](Get-NetTCPConnection -LocalPort $puerto -State Listen -ErrorAction SilentlyContinue)
}

function Hay-Conexion([string]$servidor, [int]$puerto) {
    $cliente = New-Object System.Net.Sockets.TcpClient
    try {
        $intento = $cliente.BeginConnect($servidor, $puerto, $null, $null)
        return ($intento.AsyncWaitHandle.WaitOne(4000) -and $cliente.Connected)
    } catch {
        return $false
    } finally {
        $cliente.Close()
    }
}

Write-Host ""
Write-Host "=== Verificando requisitos para Sistema de Seguimiento Académico ===" -ForegroundColor Cyan
Write-Host ""

# 1. Java 21 (Maven usa JAVA_HOME si está definido; si no, el java del PATH)
$javaExe = $null
if ($env:JAVA_HOME) {
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (-not (Test-Path $javaExe)) {
        Escribir-Falta "JAVA_HOME apunta a una carpeta sin Java: $env:JAVA_HOME"
        $faltantes.Add("Corregir la variable JAVA_HOME (o borrarla) y reinstalar Java $JavaRequerido`n" +
                       "     Descarga: https://adoptium.net/temurin/releases/?version=$JavaRequerido")
        $javaExe = $null
    }
} else {
    $javaEnPath = Get-Command java -ErrorAction SilentlyContinue
    if ($javaEnPath) { $javaExe = $javaEnPath.Source }
}

if ($javaExe) {
    $version = Obtener-VersionJava $javaExe
    if ($version -and $version -ge $JavaRequerido) {
        Escribir-Ok "Java $version"
    } else {
        $detectado = if ($version) { "Java $version" } else { "una versión de Java desconocida" }
        Escribir-Falta "Se encontró $detectado y se necesita Java $JavaRequerido o superior"
        $faltantes.Add("Java $JavaRequerido (JDK). Descarga: https://adoptium.net/temurin/releases/?version=$JavaRequerido`n" +
                       "     O en PowerShell: winget install EclipseAdoptium.Temurin.$JavaRequerido.JDK`n" +
                       "     Al instalar, marcar la opción 'Set JAVA_HOME variable'.")
    }
} elseif (-not $env:JAVA_HOME) {
    Escribir-Falta "Java no está instalado"
    $faltantes.Add("Java $JavaRequerido (JDK). Descarga: https://adoptium.net/temurin/releases/?version=$JavaRequerido`n" +
                   "     O en PowerShell: winget install EclipseAdoptium.Temurin.$JavaRequerido.JDK`n" +
                   "     Al instalar, marcar la opción 'Set JAVA_HOME variable'.")
}

# 2. Base de datos: Docker (recomendado) o un PostgreSQL ya instalado en el puerto 5432
$postgresEscuchando = Puerto-EnUso 5432
if (Get-Command docker -ErrorAction SilentlyContinue) {
    & docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        & docker compose version 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Escribir-Ok "Docker está instalado y abierto"
        } else {
            Escribir-Falta "Docker no tiene el comando 'docker compose'"
            $faltantes.Add("Actualizar Docker Desktop: https://www.docker.com/products/docker-desktop/")
        }
        # Otro proyecto con su propia base en el 5432 impediría levantar la de este
        $otroContenedor = & docker ps --filter "publish=5432" --format "{{.Names}}" 2>$null |
            Where-Object { $_ -ne 'seguimiento-academico-db' } | Select-Object -First 1
        if ($otroContenedor) {
            Escribir-Falta "El puerto 5432 lo usa el contenedor '$otroContenedor' de otro proyecto"
            $faltantes.Add("Liberar el puerto 5432 deteniendo ese contenedor: docker stop $otroContenedor`n" +
                           "     (se vuelve a encender con: docker start $otroContenedor)")
        }
    } elseif ($postgresEscuchando) {
        Escribir-Aviso "Docker está cerrado, pero hay un PostgreSQL en el puerto 5432; se usará ese"
    } else {
        Escribir-Falta "Docker está instalado pero no está abierto"
        $faltantes.Add("Abrir Docker Desktop y esperar a que diga 'Engine running' (ballena verde abajo a la izquierda)")
    }
} elseif ($postgresEscuchando) {
    Escribir-Aviso "No hay Docker, pero hay un PostgreSQL en el puerto 5432. Debe tener la base"
    Write-Host "           'seguimiento_academico' con usuario 'upe' y contraseña 'upe'." -ForegroundColor Yellow
} else {
    Escribir-Falta "Docker no está instalado (se usa para la base de datos PostgreSQL)"
    $faltantes.Add("Docker Desktop. Descarga: https://www.docker.com/products/docker-desktop/`n" +
                   "     O en PowerShell: winget install Docker.DockerDesktop`n" +
                   "     Después de instalar, reiniciar la PC y abrir Docker Desktop.")
}

# 3. Internet: la primera ejecución descarga Maven y las librerías del proyecto
$m2 = Join-Path $env:USERPROFILE '.m2'
$yaDescargado = (Test-Path (Join-Path $m2 'wrapper')) -and (Test-Path (Join-Path $m2 'repository'))
if (Hay-Conexion 'repo.maven.apache.org' 443) {
    Escribir-Ok "Conexión a Internet (repositorio de Maven)"
} elseif ($yaDescargado) {
    Escribir-Aviso "Sin conexión a Internet; se usarán las librerías ya descargadas"
} else {
    Escribir-Falta "Sin conexión a Internet (la primera vez se descargan ~150 MB de librerías)"
    $faltantes.Add("Conexión a Internet para la primera ejecución")
}

# 4. Puerto de la aplicación
if (Puerto-EnUso 8080) {
    Escribir-Aviso "El puerto 8080 está ocupado (¿la aplicación ya está abierta en otra ventana?)."
    Write-Host "           Cerrar ese programa antes de iniciar." -ForegroundColor Yellow
} else {
    Escribir-Ok "Puerto 8080 libre"
}

# 5. Git (opcional: solo hace falta para descargar o subir cambios)
if (Get-Command git -ErrorAction SilentlyContinue) {
    Escribir-Ok "Git"
} else {
    Escribir-Aviso "Git no está en el PATH (solo hace falta para subir cambios): https://git-scm.com/download/win"
}

Write-Host ""
if ($faltantes.Count -eq 0) {
    Write-Host "=== Todo listo. Se puede ejecutar iniciar.cmd ===" -ForegroundColor Green
    exit 0
}

Write-Host "=== FALTA INSTALAR O CORREGIR ($($faltantes.Count)) ===" -ForegroundColor Red
for ($i = 0; $i -lt $faltantes.Count; $i++) {
    Write-Host "  $($i + 1). $($faltantes[$i])"
}
Write-Host ""
Write-Host "Después de instalar algo, cerrar esta ventana (y VS Code) y volver a verificar." -ForegroundColor Yellow
exit 1
