@off
:: Cambia el directorio activo a la carpeta donde está guardado este archivo .bat
cd /d "%~dp0"

:: Ejecuta el archivo JAR de JavaFX utilizando la ruta relativa
java -jar "out\artifacts\nuevo_hogar_jar\nuevo-hogar.jar"

pause
