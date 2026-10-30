@echo off
REM Doble clic: verifica requisitos, levanta la base de datos, arranca el backend y abre la pagina web.
title Seguimiento Academico - Todo (base de datos + backend)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\iniciar-todo.ps1"
echo.
pause
