@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo        FlowState Windows Activity Tracker
echo ===================================================
echo.

where python >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Python is not found in your PATH.
    echo Please install Python 3.10 or higher from https://python.org
    pause
    exit /b 1
)

echo Checking dependencies (psutil, requests)...
python -c "import psutil, requests" >nul 2>nul
if %errorlevel% neq 0 (
    echo Installing required dependencies...
    pip install psutil requests
    if %errorlevel% neq 0 (
        echo [ERROR] Failed to install dependencies.
        pause
        exit /b 1
    )
)

echo.
echo Configuration Options:
echo   1) Start Tracker (Normal)
echo   2) Start Tracker with Custom Server URL
echo   3) Revoke Tracking Consent
echo   4) Logout and Clear Saved Credentials
echo.
set /p choice="Select an option (1-4, default 1): "
if "%choice%"=="" set choice=1

if "%choice%"=="1" (
    python "%~dp0tracker.py"
) else if "%choice%"=="2" (
    set /p server="Enter FlowState Server URL (e.g. http://192.168.1.50:8080): "
    set /p email="Enter account email: "
    python "%~dp0tracker.py" --server "!server!" --email "!email!"
) else if "%choice%"=="3" (
    python "%~dp0tracker.py" --revoke-consent
) else if "%choice%"=="4" (
    python "%~dp0tracker.py" --logout
) else (
    echo Invalid selection.
)

pause
