# TournamentKit PORTAL smoke test — exercises /portal/* (Firebase ID token auth) end to end.
#
# PREREQUISITES (two terminals BEFORE this script):
#
#   1) Start the Firebase emulator (from the repo root) — Firestore on 8080, Auth on 9099.
#      Use an explicit project id so the Auth token audience matches the server's GCP_PROJECT:
#        firebase emulators:start --project tournamentkit-local
#
#   2) Start the Ktor server (from kotlin/) pointed at BOTH emulators, dev seeding on, port 8090.
#      GCP_PROJECT MUST equal the emulator's --project (token audience is validated):
#        $env:FIRESTORE_EMULATOR_HOST    = "localhost:8080"
#        $env:FIREBASE_AUTH_EMULATOR_HOST = "localhost:9099"
#        $env:GCP_PROJECT                = "tournamentkit-local"
#        $env:DEV_MODE                   = "true"
#        $env:PORT                       = "8090"
#        .\gradlew.bat :server:run
#
#   3) Run this script:
#        powershell -ExecutionPolicy Bypass -File .\scripts\portal-smoke-test.ps1
#
# Prints PASS/FAIL per step; exits non-zero on any failure.

$ErrorActionPreference = "Stop"
$BaseUrl  = "http://localhost:8090"
$AuthHost = "http://localhost:9099"
$script:Failed = 0

function Pass([string]$m) { Write-Host "PASS  $m" -ForegroundColor Green }
function Fail([string]$m) { Write-Host "FAIL  $m" -ForegroundColor Red; $script:Failed++ }
function Check([bool]$c, [string]$l) { if ($c) { Pass $l } else { Fail $l } }

function Api([string]$method, [string]$path, $body, $headers) {
    $json = $null
    if ($null -ne $body) { $json = ($body | ConvertTo-Json -Depth 8) }
    return Invoke-RestMethod -Method $method -Uri "$BaseUrl$path" -Headers $headers -ContentType "application/json" -Body $json
}

# Calls an endpoint expected to FAIL with a given HTTP status; returns $true if it did.
function ExpectStatus([int]$want, [scriptblock]$call) {
    try { & $call | Out-Null; return $false }
    catch { return ($_.Exception.Response.StatusCode.value__ -eq $want) }
}

Write-Host "== Portal smoke test against $BaseUrl ==" -ForegroundColor Cyan

# ---------- 0. sign up an admin in the AUTH EMULATOR ----------
# Unique email per run so re-runs do not hit EMAIL_EXISTS in the emulator.
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
try {
    $signUp = Invoke-RestMethod -Method POST `
        -Uri "$AuthHost/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-key" `
        -ContentType "application/json" `
        -Body (@{ email = "admin+$stamp@example.com"; password = "password123"; returnSecureToken = $true } | ConvertTo-Json)
    $idToken = $signUp.idToken
    $uid     = $signUp.localId
    Check ($idToken.Length -gt 0) "admin signed up in auth emulator (uid=$uid)"
} catch { Fail "auth emulator sign-up failed: $($_.Exception.Message)"; Write-Host "Is the Auth emulator on 9099?" -ForegroundColor Yellow; exit 1 }

# Portal auth header (Firebase ID token).
$P = @{ "Authorization" = "Bearer $idToken" }

# ---------- 1. seed, claiming ownership ----------
$seed = Invoke-RestMethod -Method POST -Uri "$BaseUrl/dev/seed" -ContentType "application/json" -Body (@{ ownerUid = $uid } | ConvertTo-Json)
$projId = $seed.projectId
Check ($projId -eq "dev-project") "seeded dev-project owned by admin"

# API-key header for /v1 calls.
$A = @{ "X-TK-API-KEY" = $seed.apiKey; "X-TK-PROJECT-ID" = $projId }

# ---------- 2. portal: list + create templates ----------
# At least the 2 seeded templates exist (a re-run against a non-fresh emulator may have more).
$templates = Api GET "/portal/projects/$projId/templates" $null $P
Check ($templates.Count -ge 2) "portal lists the seeded templates (>=2, got $($templates.Count))"

$newTmpl = Api POST "/portal/projects/$projId/templates" @{ type = "KNOCKOUT"; scoring = @{ win = 3; draw = 1; loss = 0 }; maxParticipants = 8 } $P
Check ($null -ne $newTmpl.id) "portal created a template ($($newTmpl.id))"

# ---------- 3. /v1: create + start a knockout (API key) ----------
$ko = Api POST "/v1/tournaments" @{ templateId = $seed.knockoutTemplateId; name = "Portal KO"; userId = "p1"; displayName = "P1" } $A
$koId = $ko.id
foreach ($i in 2..4) { Api POST "/v1/tournaments/join" @{ joinCode = $ko.joinCode; userId = "p$i"; displayName = "P$i" } $A | Out-Null }
$started = Api POST "/v1/tournaments/$koId/start" @{ userId = "p1" } $A
Check ($started.status -eq "ACTIVE") "knockout started (ACTIVE)"

# ---------- 4. freeze -> /v1 report blocked -> unfreeze ----------
$frozen = Api POST "/portal/projects/$projId/tournaments/$koId/freeze" @{} $P
Check ($frozen.status -eq "FROZEN") "portal froze the tournament"

$view = Api GET "/v1/tournaments/$koId" $null $A
$m0 = @($view.matches | Where-Object { $_.round -eq 1 })[0]
$blocked = ExpectStatus 409 { Api POST "/v1/matches/report" @{ tournamentId = $koId; matchId = $m0.id; userId = $m0.homeId; score = @{ home = 2; away = 1 } } $A }
Check $blocked "/v1 report returns 409 while FROZEN"

$unfrozen = Api POST "/portal/projects/$projId/tournaments/$koId/unfreeze" @{} $P
Check ($unfrozen.status -eq "ACTIVE") "portal unfroze the tournament"

# ---------- 5. report a match, then portal-override it ----------
Api POST "/v1/matches/report" @{ tournamentId = $koId; matchId = $m0.id; userId = $m0.homeId; score = @{ home = 2; away = 1 } } $A | Out-Null
Pass "reported a semifinal via /v1"

# Override that match's result (it is CONFIRMED but its downstream final is still PENDING -> allowed).
$ov = Api POST "/portal/projects/$projId/tournaments/$koId/matches/$($m0.id)/override" @{ score = @{ home = 0; away = 3 }; reason = "scoreboard error" } $P
Pass "portal overrode the semifinal result"

# ---------- 6. audit log has OVERRIDE_RESULT + FREEZE ----------
$audit = Api GET "/portal/projects/$projId/tournaments/$koId/audit" $null $P
$actions = @($audit | ForEach-Object { $_.action })
Check ($actions -contains "OVERRIDE_RESULT") "audit log has OVERRIDE_RESULT"
Check ($actions -contains "FREEZE") "audit log has FREEZE"
Check ($actions -contains "UNFREEZE") "audit log has UNFREEZE"

# ---------- 7. rotate API key: old key 401, new key works ----------
$rot = Api POST "/portal/projects/$projId/keys/rotate" @{} $P
$newKey = $rot.apiKey
Check ($newKey.Length -ge 32) "rotate returned a new key once"

$oldKeyRejected = ExpectStatus 401 { Api GET "/v1/tournaments/$koId" $null $A }
Check $oldKeyRejected "old API key now returns 401 on /v1"

$A2 = @{ "X-TK-API-KEY" = $newKey; "X-TK-PROJECT-ID" = $projId }
$viaNew = Api GET "/v1/tournaments/$koId" $null $A2
Check ($viaNew.tournament.id -eq $koId) "new API key works on /v1"

# ---------- 8. analytics ----------
$analytics = Api GET "/portal/projects/$projId/analytics" $null $P
Check ($analytics.tournamentsTotal -ge 1) "analytics: tournamentsTotal >= 1 (got $($analytics.tournamentsTotal))"
Check ($analytics.matchesConfirmed -ge 1) "analytics: matchesConfirmed >= 1 (got $($analytics.matchesConfirmed))"

# ---------- 9. ownership: a different user is forbidden ----------
$other = Invoke-RestMethod -Method POST `
    -Uri "$AuthHost/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-key" `
    -ContentType "application/json" `
    -Body (@{ email = "intruder+$stamp@example.com"; password = "password123"; returnSecureToken = $true } | ConvertTo-Json)
$Pother = @{ "Authorization" = "Bearer $($other.idToken)" }
$forbidden = ExpectStatus 403 { Api GET "/portal/projects/$projId/templates" $null $Pother }
Check $forbidden "non-owner gets 403 on portal endpoints"

# ---------- summary ----------
Write-Host ""
if ($script:Failed -eq 0) { Write-Host "ALL CHECKS PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "$($script:Failed) CHECK(S) FAILED" -ForegroundColor Red; exit 1 }
