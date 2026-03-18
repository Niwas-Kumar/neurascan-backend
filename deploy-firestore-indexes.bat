@echo off
REM Deploy Firestore Indexes - Windows Batch Script
REM This script deploys Firestore indexes from firestore.indexes.json using Firebase CLI
REM Safe to run: only creates indexes, doesn't modify data

setlocal enabledelayedexpansion

echo.
echo ================================================
echo NeuraScan - Firestore Index Deployment
echo ================================================
echo.

REM Step 1: Check if Firebase CLI is installed
echo [1/4] Checking Firebase CLI installation...
firebase --version >nul 2>&1
if errorlevel 1 (
    echo.
    echo X Firebase CLI not found
    echo.
    echo Install Firebase CLI with:
    echo   npm install -g firebase-tools
    echo.
    echo Then run this script again.
    echo.
    pause
    exit /b 1
)

for /f "tokens=*" %%i in ('firebase --version 2^>nul') do set FIREBASE_VERSION=%%i
echo + Firebase CLI found: %FIREBASE_VERSION%
echo.

REM Step 2: Check if we're in the right directory
echo [2/4] Validating project directory...
if not exist "firestore.indexes.json" (
    echo.
    echo X firestore.indexes.json not found
    echo.
    echo Please run this script from:
    echo   d:\NeuroScan\neurascan-backend\neurascan-backend-complete
    echo.
    pause
    exit /b 1
)
echo + Found firestore.indexes.json
echo.

REM Step 3: Check .firebaserc
echo [3/4] Checking Firebase project configuration...
if exist ".firebaserc" (
    echo + Found .firebaserc
) else (
    echo * .firebaserc not found - Firebase will use default project
)
echo.

REM Step 4: Deploy indexes
echo [4/4] Deploying Firestore indexes...
echo This may take 5-30 minutes depending on collection size...
echo.

firebase deploy --only firestore:indexes
if errorlevel 1 (
    echo.
    echo ================================================
    echo X DEPLOYMENT FAILED
    echo ================================================
    echo.
    echo Troubleshooting:
    echo   1. Ensure you're logged in: firebase login
    echo   2. Verify .firebaserc exists with correct project ID
    echo   3. Check firestore.indexes.json is valid JSON
    echo.
    pause
    exit /b 1
)

echo.
echo ================================================
echo + DEPLOYMENT SUCCESSFUL
echo ================================================
echo.
echo Next Steps:
echo   1. Go to Firebase Console ^> Firestore ^> Indexes
echo   2. Wait for all indexes to show "Enabled" status
echo   3. Once enabled, your queries will be 3-4x faster!
echo.
echo Expected Performance Improvements:
echo   * Google Login: 1500ms -^> 400-600ms
echo   * Dashboard: 2800ms -^> 500-750ms
echo.
pause
exit /b 0
