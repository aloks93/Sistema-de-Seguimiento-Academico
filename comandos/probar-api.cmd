@echo off
REM Prueba la API con los usuarios de ejemplo (el backend tiene que estar corriendo).
title Seguimiento Academico - Probar API
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\probar-api.ps1"
echo.
pause
