@echo off
setlocal

set "SCRIPT_DIR=%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
  echo mvn was not found in PATH. Install Maven 3.9+ or run the build inside the provided Docker image. 1>&2
  exit /b 1
)

call mvn -f "%SCRIPT_DIR%pom.xml" %*
exit /b %errorlevel%
