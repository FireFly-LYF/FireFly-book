# FireFly-book one-click start (Windows PowerShell 5.1+)
# Usage:
#   1. copy .env.example .env 并填写全部密钥（禁止空值）
#   2. .\start-all.ps1
#   .\start-all.ps1 -SkipFrontend
#   .\start-all.ps1 -InfraOnly
# Double-click: start-all.bat

param(
    [switch]$SkipInfra,
    [switch]$SkipJava,
    [switch]$SkipGateway,
    [switch]$SkipFrontend,
    [switch]$InfraOnly,
    [int]$HealthTimeoutSec = 180
)

$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot
if (-not $Root) { $Root = (Get-Location).Path }

$RequiredSecrets = @(
    "JWT_SECRET",
    "ADMIN_JWT_SECRET",
    "ADMIN_PASSWORD",
    "INTERNAL_HMAC_SECRET",
    "MYSQL_PASSWORD",
    "RABBITMQ_PASSWORD"
)

function Write-Step([string]$msg) {
    Write-Host ""
    Write-Host "==> $msg" -ForegroundColor Cyan
}

function Import-DotEnv([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return $false
    }
    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $eq = $line.IndexOf("=")
        if ($eq -lt 1) { return }
        $key = $line.Substring(0, $eq).Trim()
        $val = $line.Substring($eq + 1).Trim()
        if (($val.StartsWith('"') -and $val.EndsWith('"')) -or ($val.StartsWith("'") -and $val.EndsWith("'"))) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        Set-Item -Path ("Env:" + $key) -Value $val
    }
    return $true
}

function Assert-RequiredSecrets {
    $missing = @()
    foreach ($name in $RequiredSecrets) {
        $v = [Environment]::GetEnvironmentVariable($name, "Process")
        if ([string]::IsNullOrWhiteSpace($v)) {
            $missing += $name
        }
    }
    if ($missing.Count -gt 0) {
        Write-Host "Missing required secrets (no defaults allowed):" -ForegroundColor Red
        foreach ($m in $missing) { Write-Host "  - $m" -ForegroundColor Red }
        Write-Host ""
        Write-Host "Copy .env.example to .env, fill every value, then re-run." -ForegroundColor Yellow
        Write-Host ("  copy `"{0}`" `"{1}`"" -f (Join-Path $Root ".env.example"), (Join-Path $Root ".env"))
        exit 1
    }
    Write-Host ("  [OK] secrets loaded ({0} vars)" -f $RequiredSecrets.Count) -ForegroundColor Green
}

function Test-PortOpen([int]$Port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $iar = $c.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(400, $false)
        if ($ok -and $c.Connected) {
            $c.EndConnect($iar)
            $c.Close()
            return $true
        }
        $c.Close()
        return $false
    } catch {
        return $false
    }
}

function Wait-Port([int]$Port, [string]$Name, [int]$TimeoutSec = 120) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortOpen -Port $Port) {
            Write-Host "  [OK] $Name :$Port" -ForegroundColor Green
            return $true
        }
        Start-Sleep -Seconds 2
    }
    Write-Host "  [TIMEOUT] $Name :$Port not ready" -ForegroundColor Yellow
    return $false
}

function Start-DockerContainer([string]$Name) {
    $all = @(docker ps -a --format "{{.Names}}" 2>$null)
    if ($all -notcontains $Name) {
        Write-Host "  [SKIP] container missing: $Name (create it with docker run first)" -ForegroundColor Yellow
        return
    }
    $running = @(docker ps --format "{{.Names}}" 2>$null)
    if ($running -contains $Name) {
        Write-Host "  [OK] already running: $Name" -ForegroundColor Green
        return
    }
    Write-Host "  starting container: $Name ..."
    docker start $Name | Out-Null
}

function Start-ConsoleJob([string]$Title, [string]$WorkDir, [string]$Command) {
    $ps = Join-Path $env:SystemRoot "System32\WindowsPowerShell\v1.0\powershell.exe"
    # 显式把密钥写入子进程，避免部分环境下继承丢失
    $envBlock = ($RequiredSecrets | ForEach-Object {
        $v = [Environment]::GetEnvironmentVariable($_, "Process")
        $escaped = $v -replace "'", "''"
        "`$env:$_ = '$escaped'"
    }) -join "; "
    $script = @"
`$Host.UI.RawUI.WindowTitle = '$Title'
Set-Location -LiteralPath '$WorkDir'
$envBlock
Write-Host '==== $Title ====' -ForegroundColor Cyan
$Command
Write-Host ''
Write-Host 'Process ended. You can close this window.' -ForegroundColor DarkGray
"@
    Start-Process -FilePath $ps -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", $script
    ) | Out-Null
    Write-Host "  opened window: $Title" -ForegroundColor Green
}

Write-Host "FireFly-book start-all" -ForegroundColor Magenta
Write-Host "Root: $Root"

Write-Step "Load secrets from .env"
$envFile = Join-Path $Root ".env"
if (-not (Import-DotEnv -Path $envFile)) {
    Write-Host ("  [FAIL] missing {0}" -f $envFile) -ForegroundColor Red
    Write-Host "  copy .env.example to .env and fill all values (no defaults)." -ForegroundColor Yellow
    exit 1
}
Assert-RequiredSecrets

$mediaDir = "D:\FireFlyData\media"
if (-not (Test-Path $mediaDir)) {
    New-Item -ItemType Directory -Path $mediaDir -Force | Out-Null
    Write-Host "Created media dir: $mediaDir"
}

if (-not $SkipInfra) {
    Write-Step "Docker infra (mysql/redis/rabbitmq/es)"
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Write-Host "docker not found in PATH" -ForegroundColor Red
        exit 1
    }
    foreach ($c in @("mysql", "redis", "rabbitmq", "es")) {
        Start-DockerContainer -Name $c
    }
    Write-Step "Wait infra ports"
    [void](Wait-Port -Port 3306 -Name "MySQL" -TimeoutSec 90)
    [void](Wait-Port -Port 6379 -Name "Redis" -TimeoutSec 60)
    [void](Wait-Port -Port 5672 -Name "RabbitMQ" -TimeoutSec 90)
    [void](Wait-Port -Port 9200 -Name "Elasticsearch" -TimeoutSec 120)
}

if ($InfraOnly) {
    Write-Host ""
    Write-Host "Infra-only mode done." -ForegroundColor Green
    exit 0
}

$javaServices = @(
    @{ Name = "user-service";    Port = 9001 },
    @{ Name = "content-service"; Port = 9002 },
    @{ Name = "media-service";   Port = 9003 },
    @{ Name = "social-service";  Port = 9004 },
    @{ Name = "notify-service";  Port = 9005 },
    @{ Name = "feed-service";    Port = 9006 },
    @{ Name = "search-service";  Port = 9007 }
)

$waitJavaPorts = @()
if (-not $SkipJava) {
    Write-Step "Install firefly-internal-auth (shared HMAC lib)"
    $authPom = Join-Path $Root "services\firefly-internal-auth\pom.xml"
    $mvnwInstall = Join-Path $Root "services\user-service\mvnw.cmd"
    if ((Test-Path $authPom) -and (Test-Path $mvnwInstall)) {
        & $mvnwInstall -f $authPom install -DskipTests -q
        if ($LASTEXITCODE -ne 0) {
            Write-Host "  [FAIL] firefly-internal-auth install failed" -ForegroundColor Red
        } else {
            Write-Host "  [OK] com.firefly:firefly-internal-auth installed" -ForegroundColor Green
        }
    } else {
        Write-Host "  [SKIP] auth module or mvnw missing" -ForegroundColor Yellow
    }

    Write-Step "Java services (one window each)"
    foreach ($svc in $javaServices) {
        $dir = Join-Path $Root ("services\" + $svc.Name)
        if (-not (Test-Path $dir)) {
            Write-Host ("  [SKIP] missing dir: " + $svc.Name) -ForegroundColor Yellow
            continue
        }
        if (Test-PortOpen -Port $svc.Port) {
            Write-Host ("  [OK] port busy, skip " + $svc.Name + " :" + $svc.Port) -ForegroundColor Green
            $waitJavaPorts += $svc
            continue
        }
        $mvnw = Join-Path $dir "mvnw.cmd"
        if (-not (Test-Path $mvnw)) {
            Write-Host ("  [SKIP] no mvnw.cmd: " + $svc.Name) -ForegroundColor Yellow
            continue
        }
        $title = "FireFly " + $svc.Name + " :" + $svc.Port
        Start-ConsoleJob -Title $title -WorkDir $dir -Command ".\mvnw.cmd spring-boot:run"
        $waitJavaPorts += $svc
        Start-Sleep -Milliseconds 800
    }

    Write-Step ("Wait Java ports (max " + $HealthTimeoutSec + "s)")
    foreach ($svc in $waitJavaPorts) {
        [void](Wait-Port -Port $svc.Port -Name $svc.Name -TimeoutSec $HealthTimeoutSec)
    }
}

if (-not $SkipGateway) {
    Write-Step "Gateway :8080"
    $gwDir = Join-Path $Root "Gateway\gateway"
    if (-not (Test-Path $gwDir)) {
        Write-Host "  Gateway dir missing" -ForegroundColor Red
    } elseif (Test-PortOpen -Port 8080) {
        Write-Host "  [OK] :8080 busy, skip Gateway" -ForegroundColor Green
    } else {
        if (-not (Get-Command go -ErrorAction SilentlyContinue)) {
            Write-Host "  go not found; install Go or start Gateway manually" -ForegroundColor Red
        } else {
            Start-ConsoleJob -Title "FireFly Gateway :8080" -WorkDir $gwDir -Command "go run ./cmd/gateway"
            [void](Wait-Port -Port 8080 -Name "Gateway" -TimeoutSec 60)
        }
    }
}

if (-not $SkipFrontend) {
    Write-Step "web-console :5173"
    $webDir = Join-Path $Root "web-console"
    if (-not (Test-Path $webDir)) {
        Write-Host "  web-console dir missing" -ForegroundColor Red
    } elseif (Test-PortOpen -Port 5173) {
        Write-Host "  [OK] :5173 busy, skip frontend" -ForegroundColor Green
    } else {
        if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
            Write-Host "  npm not found; install Node.js" -ForegroundColor Red
        } else {
            $cmd = @'
if (-not (Test-Path 'node_modules')) {
  Write-Host 'First run: npm install ...' -ForegroundColor Yellow
  npm install
}
npm run dev -- --host 0.0.0.0 --port 5173
'@
            Start-ConsoleJob -Title "FireFly web-console :5173" -WorkDir $webDir -Command $cmd
            [void](Wait-Port -Port 5173 -Name "web-console" -TimeoutSec 90)
        }
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Magenta
Write-Host " Done. Services run in separate windows."
Write-Host "========================================" -ForegroundColor Magenta
Write-Host "  UI        http://localhost:5173"
Write-Host "  Gateway   http://localhost:8080/gateway/health"
Write-Host "  MySQL     localhost:3306"
Write-Host "  Redis     localhost:6379"
Write-Host "  RabbitMQ  http://localhost:15672  (user guest / RABBITMQ_PASSWORD)"
Write-Host "  ES        http://localhost:9200"
Write-Host "  Java      127.0.0.1:9001-9007 (via Gateway only)"
Write-Host ""
Write-Host "  Stop:  .\stop-all.ps1" -ForegroundColor DarkGray
Write-Host "  Infra: .\start-all.ps1 -InfraOnly"
Write-Host "  Secrets: .env (from .env.example)" -ForegroundColor DarkGray
Write-Host ""
