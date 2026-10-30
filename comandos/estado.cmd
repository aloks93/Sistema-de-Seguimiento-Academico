@echo off
REM Muestra que partes del sistema estan encendidas.
title Seguimiento Academico - Estado
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\estado.ps1"
echo.
pause
