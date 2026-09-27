@echo off
setlocal
cd /d "%~dp0"

set "JAVA_CMD=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
"%JAVA_CMD%" -version >nul 2>&1
if errorlevel 1 (
  echo Java was not found. Install Java 21 and see README.md.
  pause
  exit /b 1
)
if not exist "target\travel-agency.jar" (
  echo Missing target\travel-agency.jar. Extract the whole ZIP first.
  pause
  exit /b 1
)
if not exist "data" mkdir "data"

set "SPRING_PROFILES_ACTIVE=demo"
set "PORT=8080"
echo Shanhai Travel is starting. Keep this window open.
echo Open http://127.0.0.1:8080/travel/ after startup completes.
echo Press Ctrl+C in this window to stop the application.
echo.
"%JAVA_CMD%" -jar "target\travel-agency.jar"
set "APP_EXIT_CODE=%ERRORLEVEL%"
echo.
echo Application stopped with exit code %APP_EXIT_CODE%.
pause
exit /b %APP_EXIT_CODE%
