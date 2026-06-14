# TournamentKit server smoke test — runs the full flow against a locally running server.
#
# PREREQUISITES (run these in two separate terminals BEFORE this script):
#
#   1) Start the Firebase emulator (from the repo root). It holds port 8080 for Firestore:
#        firebase emulators:start
#
#   2) Start the Ktor server pointed at the emulator, on a DIFFERENT port (8090),
#      with dev seeding enabled (from the kotlin/ folder):
#        $env:FIRESTORE_EMULATOR_HOST = "localhost:8080"
#        $env:GCP_PROJECT             = "tournamentkit-local"
#        $env:DEV_MODE                = "true"
#        $env:PORT                    = "8090"
#        .\gradlew.bat :server:run
#
#   3) Then run this script (from anywhere):
#        powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
#
# It prints PASS/FAIL per step and exits non-zero if any step fails.

$ErrorActionPreference = "Stop"
$BaseUrl = "http://localhost:8090"

$script:Failed = 0

# Prints a green PASS line.
function Pass([string]$msg) { Write-Host "PASS  $msg" -ForegroundColor Green }

# Prints a red FAIL line and records the failure.
function Fail([string]$msg) { Write-Host "FAIL  $msg" -ForegroundColor Red; $script:Failed++ }

# Asserts a condition, printing PASS or FAIL with the given label.
function Check([bool]$cond, [string]$label) { if ($cond) { Pass $label } else { Fail $label } }

# Calls the API; returns the parsed response or throws on HTTP error.
function Api([string]$method, [string]$path, $body, $headers) {
    $json = $null
    if ($null -ne $body) { $json = ($body | ConvertTo-Json -Depth 8) }
    return Invoke-RestMethod -Method $method -Uri "$BaseUrl$path" -Headers $headers -ContentType "application/json" -Body $json
}

Write-Host "== TournamentKit smoke test against $BaseUrl ==" -ForegroundColor Cyan

# ---------- 0. seed ----------
try {
    $seed = Api POST "/dev/seed" @{} @{}
    Check ($seed.projectId -eq "dev-project") "seed created dev-project"
} catch { Fail "seed failed: $($_.Exception.Message)"; Write-Host "Is the server running with DEV_MODE=true on $BaseUrl?" -ForegroundColor Yellow; exit 1 }

# Auth headers for all /v1 calls.
$H = @{ "X-TK-API-KEY" = $seed.apiKey; "X-TK-PROJECT-ID" = $seed.projectId }

# ---------- KNOCKOUT FLOW ----------
Write-Host "`n-- Knockout flow (4 players) --" -ForegroundColor Cyan

# create (creator p1 auto-joins)
$ko = Api POST "/v1/tournaments" @{ templateId = $seed.knockoutTemplateId; name = "KO Night"; userId = "p1"; displayName = "Player 1" } $H
Check ($ko.status -eq "REGISTRATION") "knockout created in REGISTRATION"
$koId = $ko.id
$code = $ko.joinCode

# 3 more joins
foreach ($i in 2..4) {
    $r = Api POST "/v1/tournaments/join" @{ joinCode = $code; userId = "p$i"; displayName = "Player $i" } $H
    Check ($r.participants.Count -eq $i) "p$i joined (now $i participants)"
}

# start
$started = Api POST "/v1/tournaments/$koId/start" @{ userId = "p1" } $H
Check ($started.status -eq "ACTIVE") "knockout started -> ACTIVE"

# read bracket
$view = Api GET "/v1/tournaments/$koId" $null $H
$r1 = @($view.matches | Where-Object { $_.round -eq 1 })
Check ($r1.Count -eq 2) "round 1 has 2 matches"

# report both semifinals (home wins each)
foreach ($m in $r1) {
    $rep = Api POST "/v1/matches/report" @{ tournamentId = $koId; matchId = $m.id; userId = $m.homeId; score = @{ home = 2; away = 1 } } $H
}
Pass "reported both semifinals"

# read final (the round-2 match now has both finalists)
$view2 = Api GET "/v1/tournaments/$koId" $null $H
$final = @($view2.matches | Where-Object { $_.round -eq 2 })[0]
Check ($final.homeId -ne "" -and $null -ne $final.awayId) "final has both players"

# report final
$repF = Api POST "/v1/matches/report" @{ tournamentId = $koId; matchId = $final.id; userId = $final.homeId; score = @{ home = 3; away = 0 } } $H
Check ($repF.tournament.status -eq "FINISHED") "knockout FINISHED after final"

# ratings changed (winner of final gained, default was 1200)
$rating = Api GET "/v1/ratings/$($final.homeId)" $null $H
Check ($rating.rating -ne 1200) "winner rating changed from default (now $($rating.rating))"

# ---------- LEAGUE FLOW ----------
Write-Host "`n-- League flow (4 players) --" -ForegroundColor Cyan

$lg = Api POST "/v1/tournaments" @{ templateId = $seed.leagueTemplateId; name = "League Night"; userId = "p1"; displayName = "Player 1" } $H
$lgId = $lg.id
$lcode = $lg.joinCode
foreach ($i in 2..4) { Api POST "/v1/tournaments/join" @{ joinCode = $lcode; userId = "p$i"; displayName = "Player $i" } $H | Out-Null }
$lgStarted = Api POST "/v1/tournaments/$lgId/start" @{ userId = "p1" } $H
Check ($lgStarted.status -eq "ACTIVE") "league started -> ACTIVE"

$lview = Api GET "/v1/tournaments/$lgId" $null $H
$lmatches = @($lview.matches)
Check ($lmatches.Count -eq 6) "league has 6 matches"

# report every match (home wins all, for a deterministic table)
$n = 0
foreach ($m in $lmatches) {
    Api POST "/v1/matches/report" @{ tournamentId = $lgId; matchId = $m.id; userId = $m.homeId; score = @{ home = 2; away = 0 } } $H | Out-Null
    $n++
}
Pass "reported all $n league matches"

# final table
$standings = Api GET "/v1/tournaments/$lgId/standings" $null $H
Write-Host "Final league table:" -ForegroundColor Cyan
$standings | ForEach-Object { Write-Host ("  {0}  P{1} W{2} D{3} L{4}  PF{5} PA{6}  Pts{7}" -f $_.userId, $_.played, $_.won, $_.drawn, $_.lost, $_.pointsFor, $_.pointsAgainst, $_.points) }
Check ($standings.Count -eq 4) "standings has 4 rows"

$lfinal = Api GET "/v1/tournaments/$lgId" $null $H
Check ($lfinal.tournament.status -eq "FINISHED") "league FINISHED after last match"

# ---------- summary ----------
Write-Host ""
if ($script:Failed -eq 0) {
    Write-Host "ALL CHECKS PASSED" -ForegroundColor Green
    exit 0
} else {
    Write-Host "$($script:Failed) CHECK(S) FAILED" -ForegroundColor Red
    exit 1
}
