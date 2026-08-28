[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [long]$EventId,

    [Parameter(Mandatory = $true)]
    [int]$ExpectedQuantity,

    [string]$BaseUrl = "http://100.101.76.93:8080",
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Continue"
$BaseUrl = $BaseUrl.TrimEnd("/")

if ($EventId -le 0 -or $ExpectedQuantity -le 0 -or $TimeoutSeconds -le 0) {
    throw "EventId, ExpectedQuantity와 TimeoutSeconds는 1 이상이어야 합니다."
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location $repoRoot
try {
    & docker compose run --rm `
        -e DISTRIBUTED_PHASE=verify `
        -e BASE_URL=$BaseUrl `
        -e COUPON_EVENT_ID=$EventId `
        -e COUPON_QUANTITY=$ExpectedQuantity `
        -e FINAL_VERIFICATION_TIMEOUT_SECONDS=$TimeoutSeconds `
        k6 run /scripts/distributed/coupon-burst-distributed.js

    if ($LASTEXITCODE -ne 0) {
        throw "분산 쿠폰 테스트 최종 검증이 실패했습니다. 종료 코드: $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

Write-Host "분산 쿠폰 테스트 최종 수량 검증 성공: $ExpectedQuantity" -ForegroundColor Green


