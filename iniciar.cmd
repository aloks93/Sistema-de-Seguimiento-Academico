@echo off
REM Doble clic: verifica requisitos, levanta la base de datos y ejecuta la aplicacion.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar.ps1"
echo.
pause
