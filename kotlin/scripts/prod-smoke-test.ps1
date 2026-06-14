# TournamentKit PRODUCTION smoke test — same flow as scripts/smoke-test.ps1 but against a
# deployed server (any external URL), not localhost.
#
# Two ways to supply credentials:
#   A) Staging with DEV_MODE=true — the script seeds dev-project automatically:
#        powershell -File .\scripts\prod-smoke-test.ps1 -BaseUrl https://your-service.run.app
#   B) Real production (DEV_MODE off) — pass an API key + project id you created in the portal,
#      plus the two template ids to test with:
#        powershell -File .\scripts\prod-smoke-test.ps1 -BaseUrl https://your-service.run.app `
#            -ApiKey tk_xxx -ProjectId my-project `
#            -KnockoutTemplateId tmpl-ko -LeagueTemplateId tmpl-lg
#
# Prints PASS/FAIL per step and exits non-zero if any step fails.

param(
    [Parameter(Mandatory = $true)] [string]$BaseUrl,
    [string]$ApiKey,
    [string]$ProjectId,
    [string]$KnockoutTemplateId,
    [string]$LeagueTemplateId
)

$ErrorActionPreference = "Stop"
$BaseUrl = $BaseUrl.TrimEnd("/")
$script:Failed = 0

function Pass([string]$m) { Write-Host "PASS  $m" -ForegroundColor Green }
function Fail([string]$m) { Write-Host "FAIL  $m" -ForegroundColor Red; $script:Failed++ }
function Check([bool]$c, [string]$l) { if ($c) { Pass $l } else { Fail $l } }

# Calls the API; returns the parsed response or throws on HTTP error.
function Api([string]$method, [string]$path, $body, $headers) {
    $json = $null
    if ($null -ne $body) { $json = ($body | ConvertTo-Json -Depth 8) }
    return Invoke-RestMethod -Method $method -Uri "$BaseUrl$path" -Headers $headers -ContentType "application/json" -Body $json
}

Write-Host "== TournamentKit PROD smoke test against $BaseUrl ==" -ForegroundColor Cyan

# ---------- 0. health ----------
try {
    $health = Invoke-WebRequest -Uri "$BaseUrl/health" -Method GET -UseBasicParsing
    Check ($health.StatusCode -eq 200) "health endpoint returns 200"
} catch { Fail "health check failed: $($_.Exception.Message)"; exit 1 }

# ---------- 1. obtain credentials ----------
# If no API key was given, try the dev seed route (only works when the server has DEV_MODE=true).
if ([string]::IsNullOrEmpty($ApiKey)) {
    try {
        $seed = Api POST "/dev/seed" @{} @{}
        $ApiKey = $seed.apiKey
        $ProjectId = $seed.projectId
        $KnockoutTemplateId = $seed.knockoutTemplateId
        $LeagueTemplateId = $seed.leagueTemplateId
        Pass "seeded dev-project (server has DEV_MODE=true)"
    } catch {
        Fail "no -ApiKey given and /dev/seed is unavailable (production has DEV_MODE off)."
        Write-Host "Pass -ApiKey/-ProjectId/-KnockoutTemplateId/-LeagueTemplateId for a real production run." -ForegroundColor Yellow
        exit 1
    }
}

if ([string]::IsNullOrEmpty($ProjectId) -or [string]::IsNullOrEmpty($KnockoutTemplateId) -or [string]::IsNullOrEmpty($LeagueTemplateId)) {
    Fail "-ProjectId, -KnockoutTemplateId and -LeagueTemplateId are required when -ApiKey is supplied."
    exit 1
}

# Auth headers for all /v1 calls.
$H = @{ "X-TK-API-KEY" = $ApiKey; "X-TK-PROJECT-ID" = $ProjectId }
# Unique suffix so repeated prod runs do not collide on user ids.
$run = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()

# ---------- KNOCKOUT FLOW ----------
Write-Host "`n-- Knockout flow (4 players) --" -ForegroundColor Cyan
$ko = Api POST "/v1/tournaments" @{ templateId = $KnockoutTemplateId; name = "Prod KO $run"; userId = "ko-$run-1"; displayName = "P1" } $H
Check ($ko.status -eq "REGISTRATION") "knockout created in REGISTRATION"
$koId = $ko.id
foreach ($i in 2..4) { Api POST "/v1/tournaments/join" @{ joinCode = $ko.joinCode; userId = "ko-$run-$i"; displayName = "P$i" } $H | Out-Null }
$started = Api POST "/v1/tournaments/$koId/start" @{ userId = "ko-$run-1" } $H
Check ($started.status -eq "ACTIVE") "knockout started -> ACTIVE"

$view = Api GET "/v1/tournaments/$koId" $null $H
$r1 = @($view.matches | Where-Object { $_.round -eq 1 })
Check ($r1.Count -eq 2) "round 1 has 2 matches"
foreach ($m in $r1) {
    Api POST "/v1/matches/report" @{ tournamentId = $koId; matchId = $m.id; userId = $m.homeId; score = @{ home = 2; away = 1 } } $H | Out-Null
}
Pass "reported both semifinals"

$view2 = Api GET "/v1/tournaments/$koId" $null $H
$final = @($view2.matches | Where-Object { $_.round -eq 2 })[0]
Check ($final.homeId -ne "" -and $null -ne $final.awayId) "final has both players"
$repF = Api POST "/v1/matches/report" @{ tournamentId = $koId; matchId = $final.id; userId = $final.homeId; score = @{ home = 3; away = 0 } } $H
Check ($repF.tournament.status -eq "FINISHED") "knockout FINISHED after final"

$rating = Api GET "/v1/ratings/$($final.homeId)" $null $H
Check ($rating.rating -ne 1200) "winner rating changed from default (now $($rating.rating))"

# ---------- LEAGUE FLOW ----------
Write-Host "`n-- League flow (4 players) --" -ForegroundColor Cyan
$lg = Api POST "/v1/tournaments" @{ templateId = $LeagueTemplateId; name = "Prod League $run"; userId = "lg-$run-1"; displayName = "P1" } $H
$lgId = $lg.id
foreach ($i in 2..4) { Api POST "/v1/tournaments/join" @{ joinCode = $lg.joinCode; userId = "lg-$run-$i"; displayName = "P$i" } $H | Out-Null }
$lgStarted = Api POST "/v1/tournaments/$lgId/start" @{ userId = "lg-$run-1" } $H
Check ($lgStarted.status -eq "ACTIVE") "league started -> ACTIVE"

$lmatches = @((Api GET "/v1/tournaments/$lgId" $null $H).matches)
Check ($lmatches.Count -eq 6) "league has 6 matches"
foreach ($m in $lmatches) {
    Api POST "/v1/matches/report" @{ tournamentId = $lgId; matchId = $m.id; userId = $m.homeId; score = @{ home = 2; away = 0 } } $H | Out-Null
}
Pass "reported all 6 league matches"

$standings = Api GET "/v1/tournaments/$lgId/standings" $null $H
Write-Host "Final league table:" -ForegroundColor Cyan
$standings | ForEach-Object { Write-Host ("  {0}  P{1} W{2} D{3} L{4}  PF{5} PA{6}  Pts{7}" -f $_.userId, $_.played, $_.won, $_.drawn, $_.lost, $_.pointsFor, $_.pointsAgainst, $_.points) }
Check ($standings.Count -eq 4) "standings has 4 rows"
$lfinal = Api GET "/v1/tournaments/$lgId" $null $H
Check ($lfinal.tournament.status -eq "FINISHED") "league FINISHED after last match"

# ---------- summary ----------
Write-Host ""
if ($script:Failed -eq 0) { Write-Host "ALL CHECKS PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "$($script:Failed) CHECK(S) FAILED" -ForegroundColor Red; exit 1 }
