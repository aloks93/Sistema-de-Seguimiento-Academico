@echo off
REM Detiene el backend y la base de datos (los datos se conservan).
title Seguimiento Academico - Detener todo
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\detener.ps1" -Que todo
echo.
pause
