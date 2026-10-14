@echo off
setlocal
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 goto use_local_maven
set VERSION=3.9.16
set BASE=%USERPROFILE%\.m2\wrapper\dists\apache-maven-%VERSION%
set DIST=%BASE%\apache-maven-%VERSION%
if not exist "%DIST%\bin\mvn.cmd" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest 'https://dlcdn.apache.org/maven/maven-3/%VERSION%/binaries/apache-maven-%VERSION%-bin.zip' -OutFile '%BASE%\maven.zip'; Expand-Archive -Force '%BASE%\maven.zip' '%BASE%'; Remove-Item '%BASE%\maven.zip'"
  if errorlevel 1 exit /b 1
)
call "%DIST%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
:use_local_maven
mvn %*
exit /b %ERRORLEVEL%
