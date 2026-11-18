@echo off
cd /d "%~dp0"
start "" "%~dp0runtime\bin\javaw.exe" -cp "%~dp0nuevo-hogar.jar" ar.edu.nuevohogar.Main
