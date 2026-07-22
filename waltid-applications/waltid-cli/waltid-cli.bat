@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%..\.."
set "LAUNCHER=%SCRIPT_DIR%build\install\waltid-jvm\bin\waltid.bat"

if not exist "%LAUNCHER%" (
  echo waltid-cli is not built yet.
  echo Running build...
  call "%PROJECT_DIR%\gradlew.bat" -p "%PROJECT_DIR%" :waltid-applications:waltid-cli:installJvmDist
  if errorlevel 1 exit /b 1
)

call "%LAUNCHER%" %*
exit /b %ERRORLEVEL%
