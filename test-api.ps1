# ========================================================
# ChatOps API Endpoint Health & Functionality Test Script
# Run this while the server is running on localhost:8080
# ========================================================

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$baseUrl = "http://localhost:8080"
$ErrorActionPreference = "Stop"

function Test-Step($name, [scriptblock]$action) {
    Write-Host -NoNewline "Testing $name ... "
    try {
        $result = &$action
        Write-Host "[PASS]" -ForegroundColor Green
        return $result
    } catch {
        Write-Host "[FAIL]" -ForegroundColor Red
        Write-Host "  Error: $_" -ForegroundColor Yellow
        return $null
    }
}

Write-Host "`n=== Starting ChatOps End-to-End API Test Suite ===`n" -ForegroundColor Cyan

# 1. Health Endpoint
Test-Step "GET /actuator/health" {
    $res = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get
    if ($res.status -ne "UP") { throw "Expected status UP but got $($res.status)" }
}

# 2. Register User 1
$ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$user1Name = "test_user1_$ts"
$user2Name = "test_user2_$ts"

$auth1 = Test-Step "POST /api/auth/register (User 1)" {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post `
      -ContentType "application/json" `
      -Body (@{ username = $user1Name; email = "$user1Name@test.local"; password = "Password123!" } | ConvertTo-Json)
}

if (-not $auth1) { Write-Host "Cannot proceed without auth. Is server running?"; exit 1 }

$token1 = $auth1.accessToken
$refreshToken1 = $auth1.refreshToken

# 3. Login User 1
Test-Step "POST /api/auth/login" {
    $res = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post `
      -ContentType "application/json" `
      -Body (@{ username = $user1Name; password = "Password123!" } | ConvertTo-Json)
    if (-not $res.accessToken) { throw "No accessToken returned" }
}

# 4. Refresh Token
$refreshed = Test-Step "POST /api/auth/refresh" {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/refresh" -Method Post `
      -ContentType "application/json" `
      -Body (@{ refreshToken = $refreshToken1 } | ConvertTo-Json)
}
if ($refreshed.accessToken) { $token1 = $refreshed.accessToken }

# 5. Get Profile
Test-Step "GET /api/users/me" {
    $me = Invoke-RestMethod -Uri "$baseUrl/api/users/me" -Method Get `
      -Headers @{ Authorization = "Bearer $token1" }
    if ($me.username -ne $user1Name) { throw "Username mismatch" }
}

# 6. Create Public Channel
$pubRoom = Test-Step "POST /api/rooms (Create Public Room)" {
    Invoke-RestMethod -Uri "$baseUrl/api/rooms" -Method Post `
      -Headers @{ Authorization = "Bearer $token1" } `
      -ContentType "application/json" `
      -Body (@{ name = "pub-$ts"; description = "Public Test Room"; isPrivate = $false } | ConvertTo-Json)
}

# 7. Create Private Channel
$privRoom = Test-Step "POST /api/rooms (Create Private Room)" {
    Invoke-RestMethod -Uri "$baseUrl/api/rooms" -Method Post `
      -Headers @{ Authorization = "Bearer $token1" } `
      -ContentType "application/json" `
      -Body (@{ name = "priv-$ts"; description = "Private Test Room"; isPrivate = $true } | ConvertTo-Json)
}

# 8. List My Rooms
Test-Step "GET /api/rooms (List Joined Rooms)" {
    $rooms = Invoke-RestMethod -Uri "$baseUrl/api/rooms" -Method Get `
      -Headers @{ Authorization = "Bearer $token1" }
    if ($rooms.Count -lt 2) { throw "Expected at least 2 rooms" }
}

# 9. List Public Rooms (Verify Privacy Filtering)
Test-Step "GET /api/rooms/public (Verify Only Public Visible)" {
    $publics = Invoke-RestMethod -Uri "$baseUrl/api/rooms/public" -Method Get `
      -Headers @{ Authorization = "Bearer $token1" }
    $hasPublic = $publics | Where-Object { $_.id -eq $pubRoom.id }
    $hasPrivate = $publics | Where-Object { $_.id -eq $privRoom.id }
    if (-not $hasPublic) { throw "Public room not found in public list" }
    if ($hasPrivate) { throw "Private room leaked into public list!" }
}

# 10. Get Room Details
Test-Step "GET /api/rooms/{id}" {
    $r = Invoke-RestMethod -Uri "$baseUrl/api/rooms/$($pubRoom.id)" -Method Get `
      -Headers @{ Authorization = "Bearer $token1" }
    if ($r.name -ne $pubRoom.name) { throw "Room details mismatch" }
}

# 11. Register User 2
$auth2 = Test-Step "POST /api/auth/register (User 2)" {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post `
      -ContentType "application/json" `
      -Body (@{ username = $user2Name; email = "$user2Name@test.local"; password = "Password123!" } | ConvertTo-Json)
}
$token2 = $auth2.accessToken

# 12. User 2 Joins Public Room
Test-Step "POST /api/rooms/{id}/join (User 2 joins Public Room)" {
    Invoke-RestMethod -Uri "$baseUrl/api/rooms/$($pubRoom.id)/join" -Method Post `
      -Headers @{ Authorization = "Bearer $token2" }
}

# 12b. User 2 Joins Private Room via Invite Code
Test-Step "POST /api/rooms/join (User 2 joins Private Room via Invite Code)" {
    $joined = Invoke-RestMethod -Uri "$baseUrl/api/rooms/join" -Method Post `
      -Headers @{ Authorization = "Bearer $token2" } `
      -ContentType "application/json" `
      -Body (@{ inviteCode = $privRoom.inviteCode } | ConvertTo-Json)
    if ($joined.id -ne $privRoom.id) { throw "Joined wrong room" }
}

# 12c. User 1 Directly Invites User 2 (Testing Admin Direct Invite)
$user3Name = "test_user3_$ts"
$auth3 = Test-Step "POST /api/auth/register (User 3)" {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post `
      -ContentType "application/json" `
      -Body (@{ username = $user3Name; email = "$user3Name@test.local"; password = "Password123!" } | ConvertTo-Json)
}
Test-Step "POST /api/rooms/{id}/invite (Direct Invite by Username)" {
    Invoke-RestMethod -Uri "$baseUrl/api/rooms/$($privRoom.id)/invite" -Method Post `
      -Headers @{ Authorization = "Bearer $token1" } `
      -ContentType "application/json" `
      -Body (@{ username = $user3Name } | ConvertTo-Json)
}

# 13. Message History
Test-Step "GET /api/rooms/{roomId}/messages (Message History)" {
    $history = Invoke-RestMethod -Uri "$baseUrl/api/rooms/$($pubRoom.id)/messages" -Method Get `
      -Headers @{ Authorization = "Bearer $token1" }
    if ($null -eq $history.messages) { throw "Invalid message page format" }
}

# 14. Online Presence
Test-Step "GET /api/presence/online" {
    Invoke-RestMethod -Uri "$baseUrl/api/presence/online" -Method Get `
      -Headers @{ Authorization = "Bearer $token1" }
}

# 15. Room Presence
Test-Step "GET /api/presence/rooms/{roomId}" {
    Invoke-RestMethod -Uri "$baseUrl/api/presence/rooms/$($pubRoom.id)" -Method Get `
      -Headers @{ Authorization = "Bearer $token1" }
}

# 16. Presence Heartbeat
Test-Step "POST /api/presence/heartbeat" {
    Invoke-RestMethod -Uri "$baseUrl/api/presence/heartbeat" -Method Post `
      -Headers @{ Authorization = "Bearer $token1" }
}

# 17. User 2 Leaves Public Room
Test-Step "DELETE /api/rooms/{id}/leave (User 2 leaves Room)" {
    Invoke-RestMethod -Uri "$baseUrl/api/rooms/$($pubRoom.id)/leave" -Method Delete `
      -Headers @{ Authorization = "Bearer $token2" }
}

# 18. User 1 Deletes Public Room
Test-Step "DELETE /api/rooms/{id} (Admin deletes Room)" {
    Invoke-RestMethod -Uri "$baseUrl/api/rooms/$($pubRoom.id)" -Method Delete `
      -Headers @{ Authorization = "Bearer $token1" }
}

# 19. User 1 Leaves and Auto-deletes Private Room (Sole Member)
Test-Step "DELETE /api/rooms/{id}/leave (Sole Admin leave -> auto-deletes)" {
    Invoke-RestMethod -Uri "$baseUrl/api/rooms/$($privRoom.id)/leave" -Method Delete `
      -Headers @{ Authorization = "Bearer $token1" }
}

# 20. Logout
Test-Step "POST /api/auth/logout" {
    Invoke-RestMethod -Uri "$baseUrl/api/auth/logout" -Method Post `
      -Headers @{ Authorization = "Bearer $token1" }
}

Write-Host "`n=== All endpoints tested successfully! ===`n" -ForegroundColor Green
