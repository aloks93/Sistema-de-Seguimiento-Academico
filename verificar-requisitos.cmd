@echo off
REM Doble clic: revisa si la computadora tiene todo lo necesario y lista lo que falta.
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\verificar-requisitos.ps1"
echo.
pause
