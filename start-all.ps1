# FireFly local dev: infra(Docker) + Java + Gateway + frontend (background, no extra PS windows)
# Usage (repo root):
#   .\start-all.ps1
#   .\start-all.ps1 -InfraOnly | -SkipFrontend | -SkipJava | -SkipGateway
#   .\start-all.ps1 -WithAi
# Stop: .\stop-all.ps1   /   .\stop-all.ps1 -StopDocker

param(
    [switch]$InfraOnly,
    [switch]$SkipFrontend,
    [switch]$SkipJava,
    [switch]$SkipGateway,
    [switch]$WithAi,
    [switch]$SkipNpmInstall,
    [switch]$SkipInternalAuthInstall,
    [switch]$SkipMediaSync
)

$ErrorActionPreference = "Stop"
$RepoRoot = $PSScriptRoot
$EnvFile = Join-Path $RepoRoot ".env"
$StateDir = Join-Path $RepoRoot ".firefly-dev"
$PidFile = Join-Path $StateDir "pids.json"
$ComposeFile = Join-Path $RepoRoot "deploy\docker-compose.yml"

$RequiredKeys = @(
    "JWT_SECRET",
    "ADMIN_JWT_SECRET",
    "ADMIN_PASSWORD",
    "INTERNAL_HMAC_SECRET",
    "MEDIA_SIGN_SECRET",
    "MYSQL_PASSWORD",
    "RABBITMQ_PASSWORD"
)

$InfraServices = @("mysql", "redis", "rabbitmq", "elasticsearch")
$JavaServices = @(
    @{ Name = "user-service";    Port = 9001 },
    @{ Name = "content-service"; Port = 9002 },
    @{ Name = "media-service";   Port = 9003 },
    @{ Name = "social-service";  Port = 9004 },
    @{ Name = "notify-service";  Port = 9005 },
    @{ Name = "feed-service";    Port = 9006 },
    @{ Name = "search-service";  Port = 9007 },
    @{ Name = "market-service";  Port = 9008 }
)

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Import-DotEnv([string]$Path) {
    foreach ($raw in Get-Content -LiteralPath $Path) {
        $line = $raw.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { continue }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        Set-Item -Path "Env:$key" -Value $val
    }
}

function Test-PortOpen([int]$Port, [string]$HostName = "127.0.0.1") {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($HostName, $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(400, $false)
        if (-not $ok) {
            $client.Close()
            return $false
        }
        $client.EndConnect($iar)
        $client.Close()
        return $true
    } catch {
        return $false
    }
}

function Wait-Port([int]$Port, [string]$Label, [int]$TimeoutSec = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen $Port) {
            Write-Host ("  OK  {0} :{1}" -f $Label, $Port) -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 2
    }
    throw ("Timeout waiting for {0} (:{1}) within {2}s" -f $Label, $Port, $TimeoutSec)
}

function Wait-Http([string]$Url, [string]$Label, [int]$TimeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $resp = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 500) {
                Write-Host ("  OK  {0} ({1})" -f $Label, $Url) -ForegroundColor Green
                return
            }
        } catch {
            # keep waiting
        }
        Start-Sleep -Seconds 2
    }
    throw ("Timeout waiting for {0} ({1}) within {2}s" -f $Label, $Url, $TimeoutSec)
}

function Ensure-LogDir {
    $logDir = Join-Path $StateDir "logs"
    if (-not (Test-Path -LiteralPath $logDir)) {
        New-Item -ItemType Directory -Path $logDir | Out-Null
    }
    return $logDir
}

function Start-DevBackground {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$CommandLine
    )
    # Hidden cmd: inherits env from this session; stdout/stderr -> log file
    $logDir = Ensure-LogDir
    $logFile = Join-Path $logDir ("{0}.log" -f $Name)
    if (Test-Path -LiteralPath $logFile) {
        Remove-Item -LiteralPath $logFile -Force -ErrorAction SilentlyContinue
    }
    $wd = $WorkingDirectory.Replace('"', '""')
    $log = $logFile.Replace('"', '""')
    $inner = $CommandLine.Replace('"', '""')
    $arg = '/c cd /d "{0}" && {1} 1>> "{2}" 2>&1' -f $wd, $inner, $log
    $proc = Start-Process -FilePath "cmd.exe" `
        -ArgumentList $arg `
        -WindowStyle Hidden `
        -PassThru
    return @{
        pid = $proc.Id
        log = $logFile
    }
}

function Sync-MediaFromDockerVolume([string]$TargetDir) {
    if ($SkipMediaSync) { return }
    $vol = "firefly_media-data"
    $inspect = docker volume inspect $vol 2>$null
    if (-not $inspect) {
        return
    }
    if (-not (Test-Path -LiteralPath $TargetDir)) {
        New-Item -ItemType Directory -Path $TargetDir -Force | Out-Null
    }
    $dockerPath = ($TargetDir -replace '\\', '/')
    Write-Host "  sync Docker volume $vol -> $TargetDir" -ForegroundColor DarkGray
    docker run --rm -v "${vol}:/from:ro" -v "${dockerPath}:/to" alpine sh -c "cp -an /from/. /to/ 2>/dev/null || true"
}

function Save-PidState([hashtable]$State) {
    if (-not (Test-Path -LiteralPath $StateDir)) {
        New-Item -ItemType Directory -Path $StateDir | Out-Null
    }
    $State | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $PidFile -Encoding UTF8
}

# ----- prechecks -----
if (-not (Test-Path -LiteralPath $EnvFile)) {
    Write-Host "Missing .env. Run: copy .env.example .env and fill secrets." -ForegroundColor Red
    exit 1
}

Write-Step "Load and validate .env"
Import-DotEnv $EnvFile
$missing = @()
foreach ($k in $RequiredKeys) {
    $v = [Environment]::GetEnvironmentVariable($k)
    if ([string]::IsNullOrWhiteSpace($v)) { $missing += $k }
}
if ($missing.Count -gt 0) {
    Write-Host ("Required secrets empty: {0}" -f ($missing -join ", ")) -ForegroundColor Red
    exit 1
}

# Force localhost for local iteration (ignore docker hostnames leftover in .env)
$env:MYSQL_HOST = "127.0.0.1"
$env:RABBITMQ_HOST = "127.0.0.1"
$env:REDIS_HOST = "127.0.0.1"
$env:ES_BASE_URL = "http://127.0.0.1:9200"
if (-not $env:RABBITMQ_USER) { $env:RABBITMQ_USER = "guest" }
if (-not $env:MEDIA_PUBLIC_BASE_URL) { $env:MEDIA_PUBLIC_BASE_URL = "http://127.0.0.1:8080/files" }
if ([string]::IsNullOrWhiteSpace($env:MEDIA_STORAGE_DIR)) {
    $mediaDir = Join-Path $RepoRoot ".firefly-dev\media"
    if (-not (Test-Path -LiteralPath $mediaDir)) {
        New-Item -ItemType Directory -Path $mediaDir -Force | Out-Null
    }
    $env:MEDIA_STORAGE_DIR = $mediaDir
} else {
    $mediaDir = $env:MEDIA_STORAGE_DIR
    if (-not (Test-Path -LiteralPath $mediaDir)) {
        New-Item -ItemType Directory -Path $mediaDir -Force | Out-Null
    }
}
Write-Step "Media storage: $mediaDir"
Sync-MediaFromDockerVolume $mediaDir

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Host "docker not found. Install/start Docker Desktop first." -ForegroundColor Red
    exit 1
}

$startedPids = @{
    java      = @()
    gateway   = $null
    frontend  = $null
    startedAt = (Get-Date).ToString("o")
}

# ----- infra -----
Write-Step "Start infra (mysql / redis / rabbitmq / elasticsearch)"
$infraArgs = @("compose", "-f", $ComposeFile, "--env-file", $EnvFile, "up", "-d") + $InfraServices
& docker @infraArgs
if ($LASTEXITCODE -ne 0) {
    throw ("docker compose infra failed (exit={0})" -f $LASTEXITCODE)
}

Write-Step "Wait for infra ports"
Wait-Port 3306 "MySQL" 180
Wait-Port 6379 "Redis" 60
Wait-Port 5672 "RabbitMQ" 90
Wait-Port 9200 "Elasticsearch" 180

if ($InfraOnly) {
    Write-Host ""
    Write-Host "Infra only ready (-InfraOnly)." -ForegroundColor Green
    Write-Host "Frontend (start web-console yourself): http://localhost:5173/" -ForegroundColor Yellow
    exit 0
}

# ----- Java -----
if (-not $SkipJava) {
    $java = Get-Command java -ErrorAction SilentlyContinue
    if (-not $java) {
        Write-Host "java not found (need JDK 17+)." -ForegroundColor Red
        exit 1
    }

    $mvnw = Join-Path $RepoRoot "services\user-service\mvnw.cmd"
    if (-not (Test-Path -LiteralPath $mvnw)) {
        throw "missing $mvnw"
    }

    if (-not $SkipInternalAuthInstall) {
        Write-Step "Install firefly-internal-auth"
        $authPom = Join-Path $RepoRoot "services\firefly-internal-auth\pom.xml"
        & $mvnw -f $authPom install "-DskipTests"
        if ($LASTEXITCODE -ne 0) {
            throw "firefly-internal-auth install failed"
        }
    }

    Write-Step "Start Java services in background (9001-9008)"
    foreach ($svc in $JavaServices) {
        if (Test-PortOpen $svc.Port) {
            Write-Host ("  skip {0} -- :{1} already listening" -f $svc.Name, $svc.Port) -ForegroundColor Yellow
            continue
        }
        $dir = Join-Path $RepoRoot ("services\{0}" -f $svc.Name)
        $localMvnw = Join-Path $dir "mvnw.cmd"
        if (-not (Test-Path -LiteralPath $localMvnw)) {
            throw "missing $localMvnw"
        }
        $bg = Start-DevBackground -Name $svc.Name -WorkingDirectory $dir -CommandLine "mvnw.cmd spring-boot:run"
        $startedPids.java += @{
            name = $svc.Name
            port = $svc.Port
            pid  = $bg.pid
            log  = $bg.log
        }
        Write-Host ("  -> {0} (pid={1}, port={2})" -f $svc.Name, $bg.pid, $svc.Port)
    }

    Write-Step "Wait for Java ports"
    foreach ($svc in $JavaServices) {
        Wait-Port $svc.Port $svc.Name 300
    }
}

# ----- Gateway -----
if (-not $SkipGateway) {
    $go = Get-Command go -ErrorAction SilentlyContinue
    if (-not $go) {
        Write-Host "go not found (need Go 1.21+)." -ForegroundColor Red
        exit 1
    }

    Write-Step "Start Gateway in background :8080"
    if (Test-PortOpen 8080) {
        Write-Host "  skip Gateway -- :8080 already listening" -ForegroundColor Yellow
    } else {
        $gwDir = Join-Path $RepoRoot "Gateway\gateway"
        $bg = Start-DevBackground -Name "gateway" -WorkingDirectory $gwDir -CommandLine "go run ./cmd/gateway"
        $startedPids.gateway = $bg.pid
        $startedPids.gatewayLog = $bg.log
        Write-Host ("  -> gateway (pid={0})" -f $bg.pid)
    }
    Wait-Http "http://127.0.0.1:8080/gateway/health" "Gateway health" 120
}

# ----- frontend -----
if (-not $SkipFrontend) {
    $npm = Get-Command npm -ErrorAction SilentlyContinue
    if (-not $npm) {
        Write-Host "npm not found (need Node.js 18+)." -ForegroundColor Red
        exit 1
    }

    $webDir = Join-Path $RepoRoot "web-console"
    $nodeModules = Join-Path $webDir "node_modules"
    if (-not $SkipNpmInstall -and -not (Test-Path -LiteralPath $nodeModules)) {
        Write-Step "npm install (web-console)"
        Push-Location $webDir
        try {
            npm install
            if ($LASTEXITCODE -ne 0) { throw "npm install failed" }
        } finally {
            Pop-Location
        }
    }

    Write-Step "Start frontend in background :5173"
    if (Test-PortOpen 5173) {
        Write-Host "  skip frontend -- :5173 already listening" -ForegroundColor Yellow
    } else {
        $bg = Start-DevBackground -Name "web-console" -WorkingDirectory $webDir -CommandLine "npm run dev"
        $startedPids.frontend = $bg.pid
        $startedPids.frontendLog = $bg.log
        Write-Host ("  -> web-console (pid={0})" -f $bg.pid)
    }
    Wait-Http "http://127.0.0.1:5173/" "Frontend" 90
}

# ----- optional AI -----
if ($WithAi) {
    Write-Step "Start AI services"
    $aiScript = Join-Path $RepoRoot "deploy\scripts\start-ai.ps1"
    if (Test-Path -LiteralPath $aiScript) {
        & $aiScript
    } else {
        Write-Host ("  missing {0}, skip" -f $aiScript) -ForegroundColor Yellow
    }
}

Save-PidState $startedPids

# ----- summary -----
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " FireFly dev environment is up" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Frontend:   http://localhost:5173/" -ForegroundColor White
Write-Host "  Gateway:    http://localhost:8080/gateway/health"
Write-Host "  RabbitMQ:   http://localhost:15672  (guest / RABBITMQ_PASSWORD in .env)"
Write-Host ""
Write-Host ("  Logs:       {0}" -f (Join-Path $StateDir "logs"))
Write-Host "  Stop apps:  .\stop-all.ps1"
Write-Host "  + Docker:   .\stop-all.ps1 -StopDocker"
Write-Host ""