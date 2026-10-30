@echo off
REM Levanta solo la base de datos PostgreSQL en Docker.
title Seguimiento Academico - Base de datos
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\iniciar-base-de-datos.ps1"
echo.
pause
