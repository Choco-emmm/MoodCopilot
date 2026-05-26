param()

$ErrorActionPreference = 'Stop'

function Write-CheckResult {
    param(
        [string]$Name,
        [bool]$Ok,
        [string]$Detail
    )

    if ($Ok) {
        Write-Host "[OK] $Name - $Detail"
    }
    else {
        Write-Host "[FAIL] $Name - $Detail"
    }
}

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$failed = 0

# 1) 关键文件完整性
$requiredFiles = @(
    "AGENTS.md",
    "package.json",
    "backend/moodcopilot/pom.xml",
    "frontend/package.json",
    "backend/moodcopilot/src/main/java/com/moodcopilot/config/AIConfiguration.java",
    "backend/moodcopilot/src/main/java/com/moodcopilot/ai/ChatService.java"
)

foreach ($file in $requiredFiles) {
    $exists = Test-Path $file
    Write-CheckResult -Name "文件检查" -Ok $exists -Detail $file
    if (-not $exists) { $failed++ }
}

# 2) 本地端口连通性（仅检测，不启动服务）
$ports = @(18080, 4173)
foreach ($port in $ports) {
    $reachable = $false
    try {
        $tnc = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue
        $reachable = [bool]$tnc.TcpTestSucceeded
    }
    catch {
        $reachable = $false
    }

    Write-CheckResult -Name "端口检测" -Ok $reachable -Detail ("127.0.0.1:{0}" -f $port)
    if (-not $reachable) { $failed++ }
}

# 3) 核心接口可达性（仅 GET，不触发写操作）
$httpTargets = @(
    @{ Name = "后端健康"; Url = "http://127.0.0.1:18080/api/health" },
    @{ Name = "前端首页"; Url = "http://127.0.0.1:4173/" }
)

foreach ($target in $httpTargets) {
    $ok = $false
    $status = "N/A"
    try {
        $resp = Invoke-WebRequest -Uri $target.Url -Method GET -TimeoutSec 3
        $status = [string]$resp.StatusCode
        $ok = $resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400
    }
    catch {
        $status = $_.Exception.Message
        $ok = $false
    }

    Write-CheckResult -Name $target.Name -Ok $ok -Detail $status
    if (-not $ok) { $failed++ }
}

if ($failed -gt 0) {
    Write-Host ("agent health check 失败项: {0}" -f $failed)
    exit 1
}

Write-Host "agent health check 通过"
exit 0
