# 🚀 Automatic Git Commit & Push Setup Guide

## Overview

This guide explains how to automatically commit and push changes to GitHub after you make modifications to the project. There are 3 methods available:

1. **Post-Commit Hook** (Automatic) - Pushes right after each commit
2. **PowerShell Script** (Manual) - Run when you want to commit changes
3. **Windows Task Scheduler** (Scheduled) - Auto-commit every X minutes

---

## Method 1: Post-Commit Hook (Recommended - Fully Automatic)

### How It Works
Every time you run `git commit`, this hook automatically pushes your changes to GitHub.

### Setup Instructions

#### On Windows (PowerShell)

1. **Copy the hook to git folder**:
   ```powershell
   Copy-Item ".git\hooks\post-commit-windows.bat" ".git\hooks\post-commit"
   ```

2. **Verify it's installed**:
   ```powershell
   Get-Content ".git\hooks\post-commit"
   ```

3. **Test it**:
   ```powershell
   # Make a test change
   Add-Content "test.txt" "test"
   git add test.txt
   git commit -m "test: auto-push test"
   
   # You should see: "📤 Auto-pushing to origin/main..."
   # Then: "✓ Push successful"
   ```

#### On Mac/Linux

1. **Copy and make executable**:
   ```bash
   cp .git/hooks/post-commit .git/hooks/post-commit-backup
   chmod +x .git/hooks/post-commit
   ```

2. **Test it**:
   ```bash
   echo "test" > test.txt
   git add test.txt
   git commit -m "test: auto-push test"
   ```

### How to Use
Just use git normally - commits will automatically push:

```powershell
# Make your changes
# Then commit
git commit -m "fix: your change description"

# Watch for: "✓ Push successful" in output
# Your changes are now on GitHub!
```

---

## Method 2: PowerShell Auto-Commit Script (Semi-Automatic)

### How It Works
Watches your project folder for changes and automatically commits + pushes them.

### Setup Instructions

#### One-Time Commit & Push
```powershell
cd "d:\NeuroScan\neurascan-backend\neurascan-backend-complete"
.\auto-commit-push.ps1 -CommitMessage "feat: your feature description"
```

**Output**:
```
[SUCCESS] All changes staged
[SUCCESS] ✓ Changes committed: feat: your feature description
[SUCCESS] ✓ Changes pushed to origin/main
```

#### Watch Mode (Continuous Monitoring)
```powershell
# Watch for changes every 30 seconds and auto-commit
.\auto-commit-push.ps1 -Watch -WatchInterval 30
```

Or with custom commit message:
```powershell
.\auto-commit-push.ps1 -Watch -CommitMessage "auto-update" -WatchInterval 30
```

**Output**:
```
[INFO] Starting watch mode (checking every 30 seconds)
[INFO] Press Ctrl+C to stop
[INFO] [14:23:45] No changes
[INFO] [14:24:15] No changes
[INFO] [14:24:45] Changes detected, committing...
[SUCCESS] ✓ Changes committed
[SUCCESS] ✓ Changes pushed
```

---

## Method 3: Windows Task Scheduler (Automatic Every X Minutes)

### How It Works
Windows Task Scheduler runs the PowerShell script every 5-10 minutes to auto-commit changes.

### Setup Instructions

1. **Open Task Scheduler**:
   - Press `Win + R`
   - Type: `taskschd.msc`
   - Click OK

2. **Create New Task**:
   - Right-click "Task Scheduler Library" → "Create Basic Task"
   - **Name**: `NeuraScan Auto Commit Push`
   - **Description**: `Automatically commit and push changes every 5 minutes`
   - Click **Next**

3. **Set Trigger**:
   - Select: **"On a schedule"** → **Repeat**
   - Choose: **"Daily"** (or your preference)
   - Set time: **Any time** (it will repeat from there)
   - Repeat every: **5 minutes**
   - Click **Next**

4. **Set Action**:
   - Action: **"Start a program"**
   - Program: `powershell.exe`
   - Arguments: `-NoProfile -ExecutionPolicy Bypass -File "d:\NeuroScan\neurascan-backend\neurascan-backend-complete\auto-commit-push.ps1"`
   - Click **Next**

5. **Finish**:
   - Click **Finish**
   - Right-click the new task → **Run** to test

### Verify It's Working

```powershell
# Make a small change
Add-Content "src/test.txt" "test"

# Wait 5 minutes (or right-click task → Run)
# Check GitHub to see if it was pushed
```

---

## Comparison Table

| Feature | Hook | Script | Task Scheduler |
|---------|------|--------|-----------------|
| **Auto-Push** | ✅ Immediately after commit | ⚠️ On demand or watch | ✅ Every X minutes |
| **Setup Time** | 1 minute | 1 minute | 5 minutes |
| **Always Active** | ✅ Yes | ⚠️ Only while running | ✅ Yes |
| **Requires Window Open** | ❌ No | ⚠️ Yes (for watch mode) | ❌ No |
| **Reliability** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Recommended** | ✅ BEST | ⚠️ Good for testing | ✅ Good for servers |

---

## Troubleshooting

### Issue: "Permission denied" error
**Solution**:
```powershell
# Allow PowerShell scripts to run
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Issue: Hook not running after commit
**Solution**:
```powershell
# Verify hook exists and is executable
Test-Path ".git\hooks\post-commit"

# Reinstall it
Copy-Item ".git\hooks\post-commit-windows.bat" ".git\hooks\post-commit" -Force
```

### Issue: "fatal: could not read Password"
**Solution**:
- Ensure you've configured GitHub SSH keys or personal access token
- Or use HTTPS with credentials stored via Windows Credential Manager

```powershell
# Test GitHub connection
git push origin main --dry-run
```

### Issue: Task Scheduler task won't run
**Solution**:
```powershell
# Run PowerShell as Administrator
# In Task Scheduler: Right-click task → "Run with highest privileges" (checkmark it)

# Test manually first
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "d:\NeuroScan\neurascan-backend\neurascan-backend-complete\auto-commit-push.ps1"
```

---

## Best Practices

### ✅ DO:
- Use **Method 1 (Hook)** for development - most automatic
- Use **Method 2 (Script)** for testing or CI/CD environments
- Use **Method 3 (Task Scheduler)** for production servers
- Write meaningful commit messages
- Review changes before pushing: `git diff`

### ❌ DON'T:
- Force push (`git push -f`) - can lose work
- Disable the hook without reason
- Commit directly without testing (`mvn clean compile` first)
- Push to `main` without pulling latest: `git pull origin main` first

---

## Quick Start

### Fastest Setup (Recommended)

```powershell
# 1. Navigate to project
cd "d:\NeuroScan\neurascan-backend\neurascan-backend-complete"

# 2. Install post-commit hook (one-time)
Copy-Item ".git\hooks\post-commit-windows.bat" ".git\hooks\post-commit"

# 3. Test it
echo "test" > test.txt
git add test.txt
git commit -m "test: setup verification"

# 4. Check GitHub - changes should be there!
```

### For Watch Mode (CI/CD Style)

```powershell
cd "d:\NeuroScan\neurascan-backend\neurascan-backend-complete"

# Terminal 1: Run watch mode in background (or Task Scheduler)
Start-Process powershell -ArgumentList "-NoProfile -Command `"& '.\auto-commit-push.ps1' -Watch -WatchInterval 30`""

# Terminal 2: Make changes normally
# Changes auto-commit every 30 seconds
```

---

## Manual Git Commands (If Not Using Auto-Push)

```powershell
# Standard workflow (without auto-push)
git status                          # See what changed
git add .                           # Stage all changes
git commit -m "your message"        # Commit
git push origin main                # MANUALLY PUSH (hook does this now!)
```

---

## Additional Resources

- **Git Hooks Documentation**: https://git-scm.com/book/en/v2/Customizing-Git-Git-Hooks
- **GitHub SSH Keys**: https://docs.github.com/en/authentication/connecting-to-github-with-ssh
- **Task Scheduler Guide**: https://www.windowscentral.com/how-create-task-scheduler-windows

---

**Version**: 1.0  
**Last Updated**: March 18, 2026  
**Status**: Production Ready ✅
