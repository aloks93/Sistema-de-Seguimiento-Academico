@echo off
REM Revisa si la computadora tiene todo lo necesario y lista lo que falta.
title Seguimiento Academico - Verificar requisitos
cd /d "%~dp0.."
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0..\scripts\verificar-requisitos.ps1"
echo.
pause
