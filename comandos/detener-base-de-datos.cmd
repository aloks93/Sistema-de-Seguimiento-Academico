@echo off
REM Detiene la base de datos (los datos se conservan).
title Seguimiento Academico - Detener base de datos
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\detener.ps1" -Que base
echo.
pause
