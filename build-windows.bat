@echo off
setlocal
cd /d "%~dp0"

where npm >nul 2>&1
if errorlevel 1 goto missing_tools
where mvn >nul 2>&1
if errorlevel 1 goto missing_tools

pushd "frontend"
call npm ci --no-audit --no-fund
if errorlevel 1 (
  popd
  goto build_failed
)
call npm run build
if errorlevel 1 (
  popd
  goto build_failed
)
popd

if exist "src\main\resources\static" rmdir /s /q "src\main\resources\static"
mkdir "src\main\resources\static"
xcopy "frontend\dist\*" "src\main\resources\static\" /E /I /Q /Y >nul
if errorlevel 1 goto build_failed
call mvn -B -ntp clean verify
if errorlevel 1 goto build_failed

echo Build passed. Run start-windows.bat to start the new JAR.
pause
exit /b 0

:missing_tools
echo Node.js, Maven, and JDK 21 are required to rebuild. See README.md.
pause
exit /b 1

:build_failed
echo Build failed. Check the error messages above.
pause
exit /b 1
