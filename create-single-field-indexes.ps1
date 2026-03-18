# Deploy Single-Field Firestore Indexes using REST API
# This script creates indexes for Google login and dashboard queries

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Deploying Single-Field Firestore Indexes" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Get Firebase access token
Write-Host "[1/6] Getting Firebase access token..." -ForegroundColor Yellow
$token = npx firebase --project neurascan-8ada2 auth:token 2>$null
if (-not $token) {
    Write-Host "Getting token via gcloud..." -ForegroundColor Gray
    # Alternative: use application default credentials
    $token = npx firebase auth:token 2>$null
}

if (-not $token) {
    Write-Host "✗ Could not get authentication token" -ForegroundColor Red
    Write-Host "Using Firebase Console instead..." -ForegroundColor Yellow
    exit 1
}

Write-Host "✓ Authentication successful" -ForegroundColor Green
Write-Host ""

# Define the indexes to create
$indexes = @(
    @{
        name = "teachers_email"
        collection = "teachers"
        fieldPath = "email"
        direction = "ASCENDING"
    },
    @{
        name = "parents_email"
        collection = "parents"
        fieldPath = "email"
        direction = "ASCENDING"
    },
    @{
        name = "students_teacherId"
        collection = "students"
        fieldPath = "teacherId"
        direction = "ASCENDING"
    },
    @{
        name = "test_papers_studentId"
        collection = "test_papers"
        fieldPath = "studentId"
        direction = "ASCENDING"
    },
    @{
        name = "analysis_reports_paperId"
        collection = "analysis_reports"
        fieldPath = "paperId"
        direction = "ASCENDING"
    }
)

$projectId = "neurascan-8ada2"
$databaseId = "(default)"
$baseUrl = "https://firestore.googleapis.com/v1"

Write-Host "[2/6] Creating single-field indexes..." -ForegroundColor Yellow
Write-Host ""

$successCount = 0
$failCount = 0

foreach ($index in $indexes) {
    Write-Host "Creating index: $($index.name)" -ForegroundColor Cyan
    
    $body = @{
        fields = @(
            @{
                fieldPath = $index.fieldPath
                order = $index.direction
            }
        )
        queryScope = "COLLECTION"
    } | ConvertTo-Json

    $url = "$baseUrl/projects/$projectId/databases/$databaseId/collectionGroups/$($index.collection)/indexes"
    
    try {
        $response = Invoke-WebRequest -Uri $url `
            -Method POST `
            -Headers @{
                "Authorization" = "Bearer $token"
                "Content-Type" = "application/json"
            } `
            -Body $body `
            -ErrorAction Stop
        
        Write-Host "  ✓ Created successfully" -ForegroundColor Green
        $successCount++
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.Value__
        if ($statusCode -eq 409) {
            Write-Host "  ⚠ Index already exists" -ForegroundColor Yellow
            $successCount++
        } else {
            Write-Host "  ✗ Error: $($_.Exception.Message)" -ForegroundColor Red
            $failCount++
        }
    }
    
    Write-Host ""
}

Write-Host "================================================" -ForegroundColor Green
Write-Host "Index Creation Summary" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Green
Write-Host "Successful: $successCount" -ForegroundColor Green
Write-Host "Failed: $failCount" -ForegroundColor Red
Write-Host ""

if ($failCount -eq 0) {
    Write-Host "All indexes created or already exist!" -ForegroundColor Green
} else {
    Write-Host "Some indexes failed to create. Check Firebase Console." -ForegroundColor Yellow
}
