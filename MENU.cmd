@echo off
REM Doble clic: menu con todas las opciones (iniciar, detener, estado, tests...).
title Seguimiento Academico - Menu
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\menu.ps1"
