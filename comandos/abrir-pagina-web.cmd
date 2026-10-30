@echo off
REM Abre la pagina web en el navegador (el backend tiene que estar corriendo).
title Seguimiento Academico - Pagina web
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\abrir-pagina-web.ps1"
echo.
pause
