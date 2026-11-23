@echo off
setlocal
cd /d "%~dp0"
if exist "%~dp0runtime\bin\javaw.exe" (
  start "" "%~dp0runtime\bin\javaw.exe" --enable-native-access=ALL-UNNAMED -cp "%~dp0nuevo-hogar.jar" ar.edu.nuevohogar.Main
  exit /b
)
where javaw.exe >nul 2>&1
if errorlevel 1 (
  echo No se encontro el runtime de Java.
  echo Extrae todo el ZIP, incluida la carpeta runtime, y vuelve a abrir este archivo.
  pause
  exit /b 1
)
start "" javaw.exe --enable-native-access=ALL-UNNAMED -cp "%~dp0nuevo-hogar.jar" ar.edu.nuevohogar.Main

