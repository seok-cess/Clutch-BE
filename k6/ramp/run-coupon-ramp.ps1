[CmdletBinding()]
param(
    [string]$BaseUrl = "http://100.101.76.93:8080",
    [string]$HealthCheckBaseUrl = "",
    [string]$PrometheusUrl = "http://100.105.168.7:9090/api/v1/write",
    [int]$TotalVus = 20000,
    [int]$CouponQuantity = 10000,
    [ValidateRange(1, 3600)]
    [int]$RampUpSeconds = 60,
    [ValidateRange(1, 3600)]
    [int]$HoldSeconds = 1,
    [long]$UserIdStart = 900001,
    [int]$MatchId = 316,
    [int]$ClaimWindowSeconds = 900,
    [int]$FinalVerificationTimeoutSeconds = 120,
    [string]$TestId = "",
    [string]$LogDirectory = ""
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
if ([string]::IsNullOrWhiteSpace($HealthCheckBaseUrl)) {
    $HealthCheckBaseUrl = $BaseUrl
}
$HealthCheckBaseUrl = $HealthCheckBaseUrl.TrimEnd("/")

if ($TotalVus -le 0 -or $CouponQuantity -le 0 -or $CouponQuantity -ge $TotalVus) {
    throw "TotalVus와 CouponQuantity 설정을 확인하세요. CouponQuantity는 TotalVus보다 작아야 합니다."
}
if ($UserIdStart -le 0 -or $MatchId -le 0 -or $ClaimWindowSeconds -le 0) {
    throw "UserIdStart, MatchId와 ClaimWindowSeconds는 1 이상이어야 합니다."
}
if ($FinalVerificationTimeoutSeconds -le 0) {
    throw "FinalVerificationTimeoutSeconds는 1 이상이어야 합니다."
}
if ([string]::IsNullOrWhiteSpace($TestId)) {
    $TestId = "$(Get-Date -Format 'yyyyMMdd-HHmmss')-coupon-${TotalVus}vus-ramp${RampUpSeconds}s"
}
if ($TestId -notmatch '^[A-Za-z0-9._-]{1,80}$') {
    throw "TestId는 80자 이하의 영문, 숫자, 점, 밑줄과 하이픈만 사용할 수 있습니다."
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
    $LogDirectory = Join-Path $repoRoot "k6/logs"
} elseif (-not [System.IO.Path]::IsPathRooted($LogDirectory)) {
    $LogDirectory = Join-Path $repoRoot $LogDirectory
}

$LogDirectory = [System.IO.Path]::GetFullPath($LogDirectory)
New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
$logFile = Join-Path $LogDirectory "$TestId.log"
if (Test-Path -LiteralPath $logFile) {
    $logFile = Join-Path $LogDirectory "$TestId-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
}

$k6Command = Get-Command k6 -ErrorAction Stop
$scenarioPath = Join-Path $repoRoot "k6/ramp/coupon-ramp.js"
$k6Args = @(
    "run",
    "--quiet",
    "-o", "experimental-prometheus-rw",
    "--tag", "testid=$TestId",
    "--tag", "loadgen=native-windows",
    $scenarioPath
)

$k6Environment = [ordered]@{
    BASE_URL = $BaseUrl
    COUPON_VUS = [string]$TotalVus
    COUPON_QUANTITY = [string]$CouponQuantity
    USER_ID_START = [string]$UserIdStart
    MATCH_ID = [string]$MatchId
    CLAIM_WINDOW_SECONDS = [string]$ClaimWindowSeconds
    RAMP_UP_SECONDS = [string]$RampUpSeconds
    HOLD_SECONDS = [string]$HoldSeconds
    CLAIM_REQUEST_TIMEOUT = "1m"
    FINAL_VERIFICATION_TIMEOUT_SECONDS = [string]$FinalVerificationTimeoutSeconds
    K6_PROMETHEUS_RW_SERVER_URL = $PrometheusUrl
    K6_PROMETHEUS_RW_PUSH_INTERVAL = "10s"
    K6_PROMETHEUS_RW_TREND_STATS = "p(50),p(95),p(99),p(99.9),avg,min,max"
}

$exitCode = 1

# Start-Transcript는 k6 같은 네이티브 프로그램의 출력을 일부 누락할 수 있다.
# 모든 안내 문구와 k6 표준 출력/오류를 같은 UTF-8 파일에 직접 기록한다.
Set-Content -LiteralPath $logFile -Value "" -Encoding UTF8
function Write-RunLog {
    param([string]$Message)
    Write-Host $Message
    Add-Content -LiteralPath $logFile -Value $Message -Encoding UTF8
}

try {
    Write-RunLog "TEST_START timestamp=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
    Write-RunLog "백엔드 연결을 확인합니다: $HealthCheckBaseUrl"
    Invoke-RestMethod -Method Get -Uri "$HealthCheckBaseUrl/actuator/health" | Out-Null

    Write-RunLog "Windows 네이티브 k6 쿠폰 부하 테스트를 시작합니다."
    Write-RunLog "TEST_CONFIG testId=$TestId loadgen=native-windows users=$TotalVus stock=$CouponQuantity rampUp=${RampUpSeconds}s hold=${HoldSeconds}s uniqueUsers=true baseUrl=$BaseUrl healthCheckBaseUrl=$HealthCheckBaseUrl"
    Write-RunLog "로그 파일: $logFile"

    Push-Location $repoRoot
    try {
        $previousEnvironment = @{}
        foreach ($entry in $k6Environment.GetEnumerator()) {
            $previousEnvironment[$entry.Key] = [Environment]::GetEnvironmentVariable(
                $entry.Key,
                [EnvironmentVariableTarget]::Process
            )
            [Environment]::SetEnvironmentVariable(
                $entry.Key,
                $entry.Value,
                [EnvironmentVariableTarget]::Process
            )
        }

        # Windows PowerShell 5.1은 네이티브 프로그램의 stderr 출력을
        # NativeCommandError로 변환할 수 있으므로 종료 코드로 성공 여부를 판정한다.
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            & $k6Command.Source @k6Args 2>&1 | ForEach-Object {
                $line = $_.ToString()
                Write-Host $line
                Add-Content -LiteralPath $logFile -Value $line -Encoding UTF8
            }
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
            foreach ($entry in $previousEnvironment.GetEnumerator()) {
                [Environment]::SetEnvironmentVariable(
                    $entry.Key,
                    $entry.Value,
                    [EnvironmentVariableTarget]::Process
                )
            }
        }
    } finally {
        Pop-Location
    }

    if ($exitCode -ne 0) {
        Write-RunLog "TEST_RESULT result=FAIL exitCode=$exitCode"
        throw "k6 실행이 실패했습니다. 종료 코드: $exitCode"
    }

    Write-RunLog "TEST_RESULT result=PASS exitCode=0"
    Write-RunLog "테스트와 최종 발급 수량 검증이 완료되었습니다."
    Write-RunLog "Grafana Test ID: $TestId"
} catch {
    Write-RunLog "ERROR message=$($_.Exception.Message)"
    throw
} finally {
    Write-RunLog "TEST_END timestamp=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
    Write-Host "로그 파일: $logFile"
}
