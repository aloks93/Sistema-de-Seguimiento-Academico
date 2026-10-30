@echo off
REM Arranca solo el backend (API + pagina web). La base de datos tiene que estar encendida.
title Seguimiento Academico - Backend
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\iniciar-backend.ps1"
echo.
pause
