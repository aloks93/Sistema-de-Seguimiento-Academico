@echo off
REM Detiene el backend.
title Seguimiento Academico - Detener backend
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\detener.ps1" -Que backend
echo.
pause
