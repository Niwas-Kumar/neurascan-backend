# Quick Setup and Deploy Script for Windows PowerShell
# This script installs Firebase CLI (if needed) and deploys Firestore indexes
# Run as Administrator: Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "NeuraScan - Firebase Setup & Index Deployment" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Check if running as Administrator
$currentUser = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($currentUser)
$isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "Note: Running without Administrator - Firebase CLI may not install correctly" -ForegroundColor Yellow
    Write-Host "For best results, run PowerShell as Administrator" -ForegroundColor Yellow
    Write-Host ""
}

# Step 1: Check and install Node.js if missing
Write-Host "[Step 1] Checking Node.js..." -ForegroundColor Yellow
$nodeExists = $null
try {
    $nodeVersion = node --version 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Node.js found: $nodeVersion" -ForegroundColor Green
    }
} catch {
    Write-Host "✗ Node.js not found" -ForegroundColor Red
    Write-Host "  Installing Node.js via npm..." -ForegroundColor Yellow
    Write-Host "  Please install Node.js manually from https://nodejs.org/" -ForegroundColor Yellow
    Write-Host "  After installation, run this script again" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# Step 2: Check and install Firebase CLI
Write-Host "[Step 2] Checking Firebase CLI..." -ForegroundColor Yellow
$firebaseExists = $null
try {
    $firebaseVersion = firebase --version 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Firebase CLI found: $firebaseVersion" -ForegroundColor Green
    }
} catch {
    Write-Host "⚠ Firebase CLI not installed" -ForegroundColor Yellow
    Write-Host "  Installing com npm..." -ForegroundColor Yellow
    
    try {
        npm install -g firebase-tools
        if ($LASTEXITCODE -eq 0) {
            $firebaseVersion = firebase --version
            Write-Host "✓ Firebase CLI installed: $firebaseVersion" -ForegroundColor Green
        } else {
            Write-Host "✗ Firebase CLI installation failed" -ForegroundColor Red
            Write-Host "  Please run: npm install -g firebase-tools" -ForegroundColor Yellow
            exit 1
        }
    } catch {
        Write-Host "✗ Firebase CLI installation failed" -ForegroundColor Red
        Write-Host "  Error: $_" -ForegroundColor Red
        Write-Host "  Try installing manually: npm install -g firebase-tools" -ForegroundColor Yellow
        exit 1
    }
}

Write-Host ""

# Step 3: Run the deployment script
Write-Host "[Step 3] Running deployment script..." -ForegroundColor Yellow
Write-Host ""

$scriptPath = Join-Path (Get-Location) "deploy-firestore-indexes.ps1"
if (Test-Path $scriptPath) {
    & $scriptPath
} else {
    Write-Host "✗ Deployment script not found: $scriptPath" -ForegroundColor Red
    exit 1
}
