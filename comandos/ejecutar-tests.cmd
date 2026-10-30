@echo off
REM Ejecuta los tests automaticos. Solo necesita Java (usa base H2 en memoria, sin Docker).
title Seguimiento Academico - Tests
cd /d "%~dp0.."
call "%~dp0..\mvnw.cmd" test
echo.
pause
