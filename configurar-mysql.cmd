@echo off
powershell.exe -NoProfile -NoExit -ExecutionPolicy Bypass -File "%~dp0scripts\setup-mysql-local.ps1" %*
