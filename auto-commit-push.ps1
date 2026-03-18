# ─── Automatic Git Commit and Push Script ──────────────────────
# Purpose: Automatically detect changes, commit, and push to remote
# Usage: .\auto-commit-push.ps1
# Shortcut: Create a Windows Task Scheduler to run this periodically

param(
    [string]$CommitMessage = "",
    [switch]$Watch = $false,
    [int]$WatchInterval = 30  # seconds
)

$ProjectPath = (Get-Location).Path
$RepoName = Split-Path -Leaf $ProjectPath
$Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

function Write-Status {
    param([string]$Message, [string]$Type = "INFO")
    $Colors = @{
        "INFO"    = "Cyan"
        "SUCCESS" = "Green"
        "WARNING" = "Yellow"
        "ERROR"   = "Red"
    }
    $Color = $Colors[$Type] ?? "White"
    Write-Host "[$Type] $Message" -ForegroundColor $Color
}

function Commit-And-Push {
    try {
        # Get current status
        $StatusOutput = git status --porcelain
        
        if ([string]::IsNullOrWhiteSpace($StatusOutput)) {
            Write-Status "No changes detected" "INFO"
            return $false
        }
        
        Write-Status "Changes detected:" "INFO"
        $StatusOutput | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
        
        # Stage all changes
        git add -A
        Write-Status "✓ All changes staged" "SUCCESS"
        
        # Create commit message
        if ([string]::IsNullOrWhiteSpace($CommitMessage)) {
            $CommitMsg = "chore: Auto-commit at $Timestamp"
        } else {
            $CommitMsg = "$CommitMessage (auto-committed at $Timestamp)"
        }
        
        # Commit
        git commit -m $CommitMsg
        Write-Status "✓ Changes committed: $CommitMsg" "SUCCESS"
        
        # Push
        git push origin main
        Write-Status "✓ Changes pushed to origin/main" "SUCCESS"
        
        return $true
        
    } catch {
        Write-Status "Error during commit/push: $_" "ERROR"
        return $false
    }
}

function Watch-And-Commit {
    Write-Status "Starting watch mode (checking every $WatchInterval seconds)" "INFO"
    Write-Status "Press Ctrl+C to stop" "WARNING"
    
    $LastCommitTime = Get-Date
    
    while ($true) {
        Start-Sleep -Seconds $WatchInterval
        
        $Status = git status --porcelain
        if (-not [string]::IsNullOrWhiteSpace($Status)) {
            Write-Status "[$Timestamp] Changes detected, committing..." "INFO"
            Commit-And-Push | Out-Null
            $LastCommitTime = Get-Date
        } else {
            Write-Status "[$((Get-Date).ToString('HH:mm:ss'))] No changes" "INFO"
        }
    }
}

# ─── Main ────────────────────────────────────────
Write-Host "
╔════════════════════════════════════════════════════════════════╗
║         🚀 Automatic Git Commit & Push Script                  ║
║         Project: $RepoName
║         Time: $Timestamp
╚════════════════════════════════════════════════════════════════╝
" -ForegroundColor Cyan

if ($Watch) {
    Watch-And-Commit
} else {
    $Result = Commit-And-Push
    if ($Result) {
        Write-Host "`n✨ All changes committed and pushed successfully!" -ForegroundColor Green
    } else {
        Write-Host "`n⚠️  No changes to commit" -ForegroundColor Yellow
    }
}
