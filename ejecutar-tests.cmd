@echo off
REM Doble clic: ejecuta los tests automaticos. Solo necesita Java (usa base H2 en memoria, sin Docker).
cd /d "%~dp0"
call "%~dp0mvnw.cmd" test
echo.
pause
