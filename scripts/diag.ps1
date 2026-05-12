function Run-StackDiagnostics($CheckPublic) {
    Write-Host "`n--- System Diagnosis ---" -ForegroundColor Cyan
    
    $backendListening = Test-NetConnection -ComputerName localhost -Port 8080 -InformationLevel Quiet
    $frontendListening = Test-NetConnection -ComputerName localhost -Port 4173 -InformationLevel Quiet
    
    Write-Host "Backend (8080): " -NoNewline
    if ($backendListening) { Write-Host "Listening" -ForegroundColor Green } else { Write-Host "Not Listening" -ForegroundColor Red }
    
    Write-Host "Frontend (4173): " -NoNewline
    if ($frontendListening) { Write-Host "Listening" -ForegroundColor Green } else { Write-Host "Not Listening" -ForegroundColor Red }
    
    Write-Host "Local Health Check: " -NoNewline
    try {
        $resp = Invoke-RestMethod -Uri "http://localhost:8080/api/health" -TimeoutSec 5
        Write-Host "OK ($($resp.status))" -ForegroundColor Green
    } catch {
        Write-Host "Failed ($($_.Exception.Message))" -ForegroundColor Red
    }

    if ($CheckPublic) {
        Write-Host "Cloudflared Status: " -NoNewline
        $cf = Get-Process cloudflared -ErrorAction SilentlyContinue
        if ($cf) { Write-Host "Running" -ForegroundColor Green } else { Write-Host "Not Running" -ForegroundColor Red }

        Write-Host "Public Health Check: " -NoNewline
        try {
            $publicUrl = "https://mood.767676.xyz/api/health"
            $resp = Invoke-RestMethod -Uri $publicUrl -TimeoutSec 5
            Write-Host "OK ($($resp.status))" -ForegroundColor Green
        } catch {
            Write-Host "Failed ($($_.Exception.Message))" -ForegroundColor Red
        }
    }
}

$diag = $args.Contains("-Diagnose")
$public = -not $args.Contains("-SkipPublicCheck")
if ($diag) { Run-StackDiagnostics -CheckPublic $public; exit 0 }
