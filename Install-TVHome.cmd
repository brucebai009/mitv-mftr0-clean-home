@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-TVHome.ps1" %*
exit /b %ERRORLEVEL%
