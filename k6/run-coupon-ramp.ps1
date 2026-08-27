[CmdletBinding()]
param(
    [string]$BaseUrl = "http://100.101.76.93:8080",
    [string]$PrometheusUrl = "http://100.105.168.7:9090/api/v1/write",
    [int]$TotalVus = 20000,
    [int]$CouponQuantity = 10000,
    [ValidateRange(1, 3600)]
    [int]$RampUpSeconds = 60,
    [ValidateRange(1, 1000000)]
    [int]$PreAllocatedVus = 5000,
    [int]$MaxVus = 0,
    [long]$UserIdStart = 900001,
    [int]$MatchId = 316,
    [int]$ClaimWindowSeconds = 900,
    [int]$FinalVerificationTimeoutSeconds = 120,
    [string]$TestId = "",
    [string]$LogDirectory = ""
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")

if ($TotalVus -le 0 -or $CouponQuantity -le 0 -or $CouponQuantity -ge $TotalVus) {
    throw "TotalVus와 CouponQuantity 설정을 확인하세요. CouponQuantity는 TotalVus보다 작아야 합니다."
}
if ($UserIdStart -le 0 -or $MatchId -le 0 -or $ClaimWindowSeconds -le 0) {
    throw "UserIdStart, MatchId와 ClaimWindowSeconds는 1 이상이어야 합니다."
}
if ($FinalVerificationTimeoutSeconds -le 0) {
    throw "FinalVerificationTimeoutSeconds는 1 이상이어야 합니다."
}
if ($MaxVus -eq 0) {
    $MaxVus = $TotalVus
}
if ($MaxVus -lt $PreAllocatedVus) {
    throw "MaxVus는 PreAllocatedVus 이상이어야 합니다."
}

if ([string]::IsNullOrWhiteSpace($TestId)) {
    $TestId = "$(Get-Date -Format 'yyyyMMdd-HHmmss')-coupon-${TotalVus}vus-ramp${RampUpSeconds}s"
}
if ($TestId -notmatch '^[A-Za-z0-9._-]{1,80}$') {
    throw "TestId는 80자 이하의 영문, 숫자, 점, 밑줄과 하이픈만 사용할 수 있습니다."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
    $LogDirectory = Join-Path $PSScriptRoot "logs"
} elseif (-not [System.IO.Path]::IsPathRooted($LogDirectory)) {
    $LogDirectory = Join-Path $repoRoot $LogDirectory
}

$LogDirectory = [System.IO.Path]::GetFullPath($LogDirectory)
New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null
$logFile = Join-Path $LogDirectory "$TestId.log"
if (Test-Path -LiteralPath $logFile) {
    $logFile = Join-Path $LogDirectory "$TestId-$(Get-Date -Format 'yyyyMMdd-HHmmss').log"
}

$dockerCommand = Get-Command docker -ErrorAction Stop
$dockerArgs = @(
    "compose", "--profile", "load-test", "run", "--rm",
    "-e", "BASE_URL=$BaseUrl",
    "-e", "COUPON_VUS=$TotalVus",
    "-e", "COUPON_QUANTITY=$CouponQuantity",
    "-e", "USER_ID_START=$UserIdStart",
    "-e", "MATCH_ID=$MatchId",
    "-e", "CLAIM_WINDOW_SECONDS=$ClaimWindowSeconds",
    "-e", "RAMP_UP_SECONDS=$RampUpSeconds",
    "-e", "PRE_ALLOCATED_VUS=$PreAllocatedVus",
    "-e", "MAX_VUS=$MaxVus",
    "-e", "CLAIM_REQUEST_TIMEOUT=1m",
    "-e", "VERIFY_INDIVIDUAL_PERSISTENCE=false",
    "-e", "FINAL_VERIFICATION_TIMEOUT_SECONDS=$FinalVerificationTimeoutSeconds",
    "-e", "K6_PROMETHEUS_RW_SERVER_URL=$PrometheusUrl",
    "-e", "K6_PROMETHEUS_RW_PUSH_INTERVAL=10s",
    "-e", "K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),p(99.9),avg,min,max",
    "k6", "run",
    "--quiet",
    "-o", "experimental-prometheus-rw",
    "--tag", "testid=$TestId",
    "--tag", "loadgen=single-laptop",
    "/scripts/coupon-ramp.js"
)

$exitCode = 1

# Start-Transcript는 Docker 같은 네이티브 프로그램의 출력을 일부 누락할 수 있다.
# 모든 안내 문구와 k6 표준 출력/오류를 같은 UTF-8 파일에 직접 기록한다.
Set-Content -LiteralPath $logFile -Value "" -Encoding UTF8
function Write-RunLog {
    param([string]$Message)
    Write-Host $Message
    Add-Content -LiteralPath $logFile -Value $Message -Encoding UTF8
}

try {
    Write-RunLog "TEST_START timestamp=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
    Write-RunLog "백엔드 연결을 확인합니다: $BaseUrl"
    Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health" | Out-Null

    Write-RunLog "단일 노트북 쿠폰 부하 테스트를 시작합니다."
    Write-RunLog "TEST_CONFIG testId=$TestId users=$TotalVus stock=$CouponQuantity rampUp=${RampUpSeconds}s preAllocatedVUs=$PreAllocatedVus maxVUs=$MaxVus"
    Write-RunLog "로그 파일: $logFile"

    Push-Location $repoRoot
    try {
        # Windows PowerShell 5.1은 Docker Compose가 stderr로 출력하는 정상 상태
        # 메시지(Container ... Creating 등)도 NativeCommandError로 변환한다.
        # 이 구간에서만 비종료 오류로 받아 실제 성공 여부는 종료 코드로 판정한다.
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            & $dockerCommand.Source @dockerArgs 2>&1 | ForEach-Object {
                $line = $_.ToString()
                Write-Host $line
                Add-Content -LiteralPath $logFile -Value $line -Encoding UTF8
            }
            $exitCode = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previousErrorActionPreference
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
