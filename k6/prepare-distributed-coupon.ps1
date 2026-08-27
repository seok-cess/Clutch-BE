[CmdletBinding()]
param(
    [string]$BaseUrl = "http://100.101.76.93:8080",
    [int]$TotalVus = 20000,
    [int]$CouponQuantity = 0,
    [int]$MatchId = 316,
    [int]$ClaimWindowSeconds = 900,
    [string]$CouponName = "[K6] 10%"
)

$ErrorActionPreference = "Continue"
$BaseUrl = $BaseUrl.TrimEnd("/")

if ($TotalVus -le 0) {
    throw "TotalVus는 1 이상이어야 합니다."
}
if ($CouponQuantity -eq 0) {
    $CouponQuantity = [int]($TotalVus / 2)
}
if ($CouponQuantity -le 0 -or $CouponQuantity -ge $TotalVus) {
    throw "CouponQuantity는 1 이상이고 TotalVus보다 작아야 합니다."
}
if ($ClaimWindowSeconds -le 0) {
    throw "ClaimWindowSeconds는 1 이상이어야 합니다."
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    Write-Host "공유 쿠폰 이벤트와 회차를 생성합니다."
    & docker compose run --rm `
        -e DISTRIBUTED_PHASE=prepare `
        -e BASE_URL=$BaseUrl `
        -e COUPON_VUS=$TotalVus `
        -e COUPON_QUANTITY=$CouponQuantity `
        -e MATCH_ID=$MatchId `
        -e CLAIM_WINDOW_SECONDS=$ClaimWindowSeconds `
        -e COUPON_NAME=$CouponName `
        k6 run /scripts/coupon-burst-distributed.js 2>&1 |
        Tee-Object -Variable capturedOutput

    if ($LASTEXITCODE -ne 0) {
        throw "공유 이벤트 준비가 실패했습니다. 종료 코드: $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$outputText = ($capturedOutput | Out-String)
$match = [regex]::Match(
    $outputText,
    "COUPON_EVENT_ID=(?<event>\d+)\s+COUPON_EVENT_OCCURRENCE_ID=(?<occurrence>\d+)"
)
if (-not $match.Success) {
    throw "k6 출력에서 이벤트 ID와 회차 ID를 찾지 못했습니다."
}

$eventId = [long]$match.Groups["event"].Value
$occurrenceId = [long]$match.Groups["occurrence"].Value

Write-Host ""
Write-Host "두 노트북에 아래 값을 동일하게 입력하세요." -ForegroundColor Green
Write-Host "EventId=$eventId"
Write-Host "OccurrenceId=$occurrenceId"

[pscustomobject]@{
    EventId = $eventId
    OccurrenceId = $occurrenceId
    TotalVus = $TotalVus
    CouponQuantity = $CouponQuantity
}


