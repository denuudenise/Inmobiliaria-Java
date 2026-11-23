@echo off
setlocal
cd /d "%~dp0"
if exist "%~dp0runtime\bin\java.exe" (
  "%~dp0runtime\bin\java.exe" --enable-native-access=ALL-UNNAMED -cp "%~dp0nuevo-hogar.jar" ar.edu.nuevohogar.Main
) else (
  java -cp "%~dp0nuevo-hogar.jar" ar.edu.nuevohogar.Main
)
echo.
echo La aplicacion se cerro. Copia el error que aparece arriba.
pause
