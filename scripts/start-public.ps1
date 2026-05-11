param(
    [switch]$Restart,
    [switch]$Build,
    [switch]$SkipPublicCheck
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$BackendDir = Join-Path $Root "backend\moodcopilot"
$FrontendDir = Join-Path $Root "frontend"
$EnvFile = Join-Path $Root ".env"
$CloudflaredConfig = "C:\Users\renpe\.cloudflared\moodcopilot-config.yaml"
$CloudflaredWinGet = "C:\Users\renpe\AppData\Local\Microsoft\WinGet\Packages\Cloudflare.cloudflared_Microsoft.Winget.Source_8wekyb3d8bbwe\cloudflared.exe"

function Write-Step($Message) {
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Import-DotEnv($Path) {
    if (-not (Test-Path $Path)) {
        throw "Missing $Path; backend database settings cannot be loaded."
    }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -le 0) { return }
        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim().Trim('"')
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Get-ListenerProcessIds($Port) {
    @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique)
}

function Test-PortListening($Port) {
    @(Get-ListenerProcessIds $Port).Count -gt 0
}

function Stop-Port($Port) {
    foreach ($pidValue in Get-ListenerProcessIds $Port) {
        Stop-Process -Id $pidValue -Force -ErrorAction SilentlyContinue
    }
}

function Wait-HttpOk($Url, $Name, $TimeoutSeconds = 60) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $res = Invoke-WebRequest $Url -UseBasicParsing -TimeoutSec 5
            if ($res.StatusCode -eq 200) {
                Write-Host "OK $Name $Url" -ForegroundColor Green
                return
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw "$Name not ready: $Url"
}

Import-DotEnv $EnvFile

if ($Restart) {
    Write-Step "Restart 18080/4173 and cloudflared"
    Stop-Port 18080
    Stop-Port 4173
    Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
}

if ($Build -or -not (Test-Path (Join-Path $FrontendDir "dist\index.html"))) {
    Write-Step "Build frontend"
    Push-Location $FrontendDir
    try {
        npm.cmd run build
    } finally {
        Pop-Location
    }
}

if (-not (Test-PortListening 18080)) {
    Write-Step "Start backend 18080"
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "mvn.cmd spring-boot:run -Dspring-boot.run.profiles=dev 1> startup.log 2>&1" `
        -WorkingDirectory $BackendDir `
        -WindowStyle Hidden
} else {
    Write-Step "Backend 18080 already listening"
}

if (-not (Test-PortListening 4173)) {
    Write-Step "Start frontend preview 4173"
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/c", "npx.cmd vite preview --host 127.0.0.1 --port 4173 1> preview-login-debug.log 2>&1" `
        -WorkingDirectory $FrontendDir `
        -WindowStyle Hidden
} else {
    Write-Step "Frontend preview 4173 already listening"
}

$cloudflared = if (Test-Path $CloudflaredWinGet) { $CloudflaredWinGet } else { "cloudflared" }
$cloudflaredRunning = @(Get-CimInstance Win32_Process -Filter "Name='cloudflared.exe'" -ErrorAction SilentlyContinue).Count -gt 0
if (-not $cloudflaredRunning) {
    Write-Step "Start Cloudflare Tunnel"
    Start-Process -FilePath $cloudflared `
        -ArgumentList "tunnel", "--config", $CloudflaredConfig, "run", "moodcopilot" `
        -WorkingDirectory $Root `
        -WindowStyle Hidden
} else {
    Write-Step "Cloudflare Tunnel already running"
}

Write-Step "Health checks"
Wait-HttpOk "http://127.0.0.1:18080/api/health" "local backend"
Wait-HttpOk "http://127.0.0.1:4173/" "local frontend"
if (-not $SkipPublicCheck) {
    Wait-HttpOk "https://moodcopilot.dpdns.org/api/health" "public API" 90
    Wait-HttpOk "https://moodcopilot.dpdns.org/" "public home" 90
}

Write-Host ""
Write-Host "Public site ready: https://moodcopilot.dpdns.org/" -ForegroundColor Green
