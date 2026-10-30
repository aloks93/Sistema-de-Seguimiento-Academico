@echo off
REM BORRA todos los datos de la base (pide confirmacion). Se vuelve a crear vacia al iniciar.
title Seguimiento Academico - Borrar base de datos
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\borrar-base-de-datos.ps1"
echo.
pause
