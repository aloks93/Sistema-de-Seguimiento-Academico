# Verifica que la computadora tenga todo lo necesario para ejecutar el proyecto
# y muestra una lista de lo que falta instalar.
# Uso: comandos\verificar-requisitos.cmd
# Código de salida: 0 = todo listo, 1 = falta algo.

. "$PSScriptRoot\comun.ps1"

$faltantes = New-Object System.Collections.Generic.List[string]
$instalarJava = "Java $JavaRequerido (JDK). Descarga: https://adoptium.net/temurin/releases/?version=$JavaRequerido`n" +
                "     O en PowerShell: winget install EclipseAdoptium.Temurin.$JavaRequerido.JDK`n" +
                "     Al instalar, marcar la opción 'Set JAVA_HOME variable'."

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

Escribir-Titulo "Verificando requisitos para Sistema de Seguimiento Académico"

# 1. Java 21 (Maven usa JAVA_HOME si está definido; si no, el java del PATH)
$javaExe = Obtener-JavaExe
if ($env:JAVA_HOME -and -not $javaExe) {
    Escribir-Falta "JAVA_HOME apunta a una carpeta sin Java: $env:JAVA_HOME"
    $faltantes.Add("Corregir la variable JAVA_HOME (o borrarla) y reinstalar Java $JavaRequerido`n" +
                   "     Descarga: https://adoptium.net/temurin/releases/?version=$JavaRequerido")
} elseif (-not $javaExe) {
    Escribir-Falta "Java no está instalado"
    $faltantes.Add($instalarJava)
} else {
    $version = Obtener-VersionJava $javaExe
    if ($version -and $version -ge $JavaRequerido) {
        Escribir-Ok "Java $version"
    } else {
        $detectado = if ($version) { "Java $version" } else { "una versión de Java desconocida" }
        Escribir-Falta "Se encontró $detectado y se necesita Java $JavaRequerido o superior"
        $faltantes.Add($instalarJava)
    }
}

# 2. Base de datos: Docker (recomendado) o un PostgreSQL ya instalado en el puerto 5432
$postgresEscuchando = Puerto-EnUso $PuertoBase
if (Docker-Instalado) {
    if (Docker-Abierto) {
        & docker compose version 2>&1 | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Escribir-Ok "Docker está instalado y abierto"
        } else {
            Escribir-Falta "Docker no tiene el comando 'docker compose'"
            $faltantes.Add("Actualizar Docker Desktop: https://www.docker.com/products/docker-desktop/")
        }
        # Otro proyecto con su propia base en el 5432 impediría levantar la de este
        $otroContenedor = Contenedor-AjenoEnPuertoBase
        if ($otroContenedor) {
            Escribir-Falta "El puerto $PuertoBase lo usa el contenedor '$otroContenedor' de otro proyecto"
            $faltantes.Add("Liberar el puerto $PuertoBase deteniendo ese contenedor: docker stop $otroContenedor`n" +
                           "     (se vuelve a encender con: docker start $otroContenedor)")
        }
    } elseif ($postgresEscuchando) {
        Escribir-Aviso "Docker está cerrado, pero hay un PostgreSQL en el puerto $PuertoBase; se usará ese"
    } else {
        Escribir-Falta "Docker está instalado pero no está abierto"
        $faltantes.Add("Abrir Docker Desktop y esperar a que diga 'Engine running' (ballena verde abajo a la izquierda)")
    }
} elseif ($postgresEscuchando) {
    Escribir-Aviso "No hay Docker, pero hay un PostgreSQL en el puerto $PuertoBase. Debe tener la base"
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
if (Backend-Corriendo) {
    Escribir-Aviso "El puerto $PuertoApp está ocupado (¿el backend ya está corriendo en otra ventana?)."
    Write-Host "           Ver con comandos\estado.cmd o detener con comandos\detener-backend.cmd" -ForegroundColor Yellow
} else {
    Escribir-Ok "Puerto $PuertoApp libre"
}

# 5. Git (opcional: solo hace falta para descargar o subir cambios)
if (Get-Command git -ErrorAction SilentlyContinue) {
    Escribir-Ok "Git"
} else {
    Escribir-Aviso "Git no está en el PATH (solo hace falta para subir cambios): https://git-scm.com/download/win"
}

Write-Host ""
if ($faltantes.Count -eq 0) {
    Write-Host "=== Todo listo. Se puede ejecutar iniciar-todo.cmd ===" -ForegroundColor Green
    exit 0
}

Write-Host "=== FALTA INSTALAR O CORREGIR ($($faltantes.Count)) ===" -ForegroundColor Red
for ($i = 0; $i -lt $faltantes.Count; $i++) {
    Write-Host "  $($i + 1). $($faltantes[$i])"
}
Write-Host ""
Write-Host "Después de instalar algo, cerrar esta ventana (y VS Code) y volver a verificar." -ForegroundColor Yellow
exit 1
