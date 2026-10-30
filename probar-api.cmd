@echo off
REM Doble clic (con iniciar.cmd ya corriendo): prueba login, registro y consulta de notas.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\probar-api.ps1"
echo.
pause
