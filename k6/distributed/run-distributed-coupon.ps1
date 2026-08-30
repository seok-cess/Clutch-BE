[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("A", "B")]
    [string]$Node,

    [Parameter(Mandatory = $true)]
    [long]$EventId,

    [Parameter(Mandatory = $true)]
    [long]$OccurrenceId,

    [Parameter(Mandatory = $true)]
    [datetime]$StartAt,

    [Parameter(Mandatory = $true)]
    [string]$TestId,

    [string]$BaseUrl = "http://100.101.76.93:8080",
    [string]$PrometheusUrl = "http://100.105.168.7:9090/api/v1/write",
    [int]$TotalVus = 20000,
    [int]$CouponQuantity = 10000,
    [ValidateRange(1, 3600)]
    [int]$RampUpSeconds = 60,
    [ValidateRange(1, 3600)]
    [int]$HoldSeconds = 1,
    [long]$UserIdStart = 900001,
    [int]$ApiPort = 6565,
    [string]$LogDirectory = ""
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

if ($EventId -le 0 -or $OccurrenceId -le 0) {
    throw "EventId와 OccurrenceId는 1 이상이어야 합니다."
}
if ($TotalVus -le 0 -or $CouponQuantity -le 0 -or $CouponQuantity -ge $TotalVus) {
    throw "TotalVus와 CouponQuantity 설정을 확인하세요. CouponQuantity는 TotalVus보다 작아야 합니다."
}
if ($TotalVus % 2 -ne 0) {
    throw "두 실행기에 정확히 나누기 위해 TotalVus는 짝수여야 합니다."
}
if ($StartAt -le (Get-Date).AddSeconds(30)) {
    throw "StartAt은 Docker 준비 시간을 고려해 현재 시각보다 최소 30초 이후로 지정하세요."
}
if ($TestId -notmatch '^[A-Za-z0-9._-]{1,40}$') {
    throw "TestId는 40자 이하의 영문, 숫자, 점, 밑줄과 하이픈만 사용할 수 있습니다."
}

$segment = if ($Node -eq "A") { "0:1/2" } else { "1/2:1" }
$loadGenerator = "laptop-$($Node.ToLowerInvariant())"
$containerName = "clutch-k6-$($TestId.ToLowerInvariant())-$($Node.ToLowerInvariant())"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$dockerCommand = Get-Command docker -ErrorAction Stop

if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
    $LogDirectory = Join-Path $repoRoot "k6/logs"
} elseif (-not [System.IO.Path]::IsPathRooted($LogDirectory)) {
    $LogDirectory = Join-Path $repoRoot $LogDirectory
}
$LogDirectory = [System.IO.Path]::GetFullPath($LogDirectory)
New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
$stdoutLog = Join-Path $LogDirectory "$TestId-$loadGenerator.log"
$stderrLog = Join-Path $LogDirectory "$TestId-$loadGenerator.stderr.log"

Write-Host "백엔드 연결을 확인합니다: $BaseUrl"
Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health" | Out-Null

$dockerArgs = @(
    "compose", "run", "--rm", "--name", $containerName,
    "-p", "${ApiPort}:6565",
    "-e", "DISTRIBUTED_PHASE=load",
    "-e", "BASE_URL=$BaseUrl",
    "-e", "COUPON_EVENT_ID=$EventId",
    "-e", "COUPON_EVENT_OCCURRENCE_ID=$OccurrenceId",
    "-e", "COUPON_VUS=$TotalVus",
    "-e", "COUPON_QUANTITY=$CouponQuantity",
    "-e", "USER_ID_START=$UserIdStart",
    "-e", "EXPECTED_CLAIM_COUNT=$($TotalVus / 2)",
    "-e", "RAMP_UP_SECONDS=$RampUpSeconds",
    "-e", "HOLD_SECONDS=$HoldSeconds",
    "-e", "VERIFY_INDIVIDUAL_PERSISTENCE=false",
    "-e", "CLAIM_REQUEST_TIMEOUT=1m",
    "-e", "K6_PROMETHEUS_RW_SERVER_URL=$PrometheusUrl",
    "-e", "K6_PROMETHEUS_RW_PUSH_INTERVAL=10s",
    "-e", "K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),p(99.9),avg,min,max",
    "k6", "run",
    "--paused",
    "--address=0.0.0.0:6565",
    "--execution-segment", $segment,
    "--execution-segment-sequence", "0,1/2,1",
    "--tag", "testid=$TestId",
    "--tag", "loadgen=$loadGenerator",
    "--quiet",
    "-o", "experimental-prometheus-rw",
    "/scripts/distributed/coupon-burst-distributed.js"
)

Write-Host "k6 실행기를 대기 상태로 시작합니다: node=$Node, segment=$segment"
Write-Host "k6 결과 로그: $stdoutLog"
Write-Host "Docker 오류 로그: $stderrLog"
$process = Start-Process `
    -FilePath $dockerCommand.Source `
    -ArgumentList $dockerArgs `
    -WorkingDirectory $repoRoot `
    -NoNewWindow `
    -RedirectStandardOutput $stdoutLog `
    -RedirectStandardError $stderrLog `
    -PassThru

$apiUrl = "http://localhost:$ApiPort/v1/status"
$apiReady = $false
$testResumed = $false

try {
    $apiDeadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $apiDeadline) {
        if ($process.HasExited) {
            throw "k6가 대기 상태에 진입하기 전에 종료되었습니다. 종료 코드: $($process.ExitCode)"
        }
        try {
            Invoke-RestMethod -Method Get -Uri $apiUrl | Out-Null
            $apiReady = $true
            break
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }

    if (-not $apiReady) {
        throw "60초 안에 k6 REST API가 준비되지 않았습니다: $apiUrl"
    }

    if ((Get-Date) -ge $StartAt) {
        throw "k6 준비 중 StartAt이 지났습니다. 더 여유 있는 시각으로 다시 실행하세요."
    }

    Write-Host "대기 준비 완료. 시작 예정 시각: $($StartAt.ToString('yyyy-MM-dd HH:mm:ss.fff'))"
    while ((Get-Date) -lt $StartAt) {
        $remaining = ($StartAt - (Get-Date)).TotalMilliseconds
        if ($remaining -gt 1000) {
            Start-Sleep -Milliseconds 500
        } else {
            Start-Sleep -Milliseconds 20
        }
    }

    $resumeBody = @{
        data = @{
            type = "status"
            id = "default"
            attributes = @{ paused = $false }
        }
    } | ConvertTo-Json -Depth 4

    Invoke-RestMethod `
        -Method Patch `
        -Uri $apiUrl `
        -ContentType "application/json" `
        -Body $resumeBody | Out-Null
    $testResumed = $true

    Write-Host "k6 부하 시작: node=$Node, rampUp=${RampUpSeconds}s, actual=$((Get-Date).ToString('yyyy-MM-dd HH:mm:ss.fff'))" -ForegroundColor Green
    $process.WaitForExit()
    $process.Refresh()

    if ($process.ExitCode -ne 0) {
        Write-Host "k6 결과 로그 마지막 80줄:" -ForegroundColor Yellow
        Get-Content -LiteralPath $stdoutLog -Tail 80 -ErrorAction SilentlyContinue
        Write-Host "Docker 오류 로그 마지막 40줄:" -ForegroundColor Yellow
        Get-Content -LiteralPath $stderrLog -Tail 40 -ErrorAction SilentlyContinue
        throw "k6 실행이 실패했습니다. 종료 코드: $($process.ExitCode)"
    }
} finally {
    if (-not $testResumed -and -not $process.HasExited) {
        & $dockerCommand.Source rm -f $containerName 2>$null | Out-Null
    }
}

Write-Host "노트북 $Node 실행 및 실행기별 threshold 검증 완료." -ForegroundColor Green
Write-Host "k6 결과 로그: $stdoutLog"
Write-Host "Docker 오류 로그: $stderrLog"
Write-Host "두 노트북이 모두 끝나면 verify-distributed-coupon.ps1을 실행하세요."

