$baseUrl = "http://localhost:8080/api/v1/auth"
$loginPayload = @{
    email = "admin@huit.edu.vn"
    password = "admin123"
} | ConvertTo-Json

Write-Host "1. Testing Login..."
$loginResponse = Invoke-RestMethod -Uri "$baseUrl/login" -Method Post -Body $loginPayload -ContentType "application/json"
$accessToken = $loginResponse.accessToken
$refreshToken = $loginResponse.refreshToken

if ($accessToken -and $refreshToken) {
    Write-Host "Login Successful!"
    Write-Host "Access Token: $($accessToken.Substring(0, 20))..."
    Write-Host "Refresh Token: $($refreshToken.Substring(0, 20))..."
} else {
    Write-Host "Login Failed!"
    exit 1
}

Write-Host "`n2. Testing Refresh Token..."
$refreshPayload = @{
    refreshToken = $refreshToken
} | ConvertTo-Json

$refreshResponse = Invoke-RestMethod -Uri "$baseUrl/refresh-token" -Method Post -Body $refreshPayload -ContentType "application/json"
$newAccessToken = $refreshResponse.accessToken
$newRefreshToken = $refreshResponse.refreshToken

if ($newAccessToken -and $newRefreshToken) {
    Write-Host "Refresh Successful!"
    Write-Host "New Access Token: $($newAccessToken.Substring(0, 20))..."
    Write-Host "New Refresh Token: $($newRefreshToken.Substring(0, 20))..."
} else {
    Write-Host "Refresh Failed!"
    exit 1
}

Write-Host "`n3. Testing Logout..."
$logoutPayload = @{
    refreshToken = $newRefreshToken
} | ConvertTo-Json

$headers = @{
    Authorization = "Bearer $newAccessToken"
}

Invoke-RestMethod -Uri "$baseUrl/logout" -Method Post -Body $logoutPayload -ContentType "application/json" -Headers $headers
Write-Host "Logout Successful!"

Write-Host "`n4. Testing Blacklisted Token (Should Fail)..."
try {
    # Let's try to access a protected endpoint, for example /api/v1/auth/me (if it exists) or anything else.
    # We can just try to refresh the token again with the blacklisted refresh token.
    $refreshPayload2 = @{
        refreshToken = $newRefreshToken
    } | ConvertTo-Json

    $failResponse = Invoke-RestMethod -Uri "$baseUrl/refresh-token" -Method Post -Body $refreshPayload2 -ContentType "application/json"
    Write-Host "Wait, this should have failed!"
} catch {
    Write-Host "Success: Token was rejected as expected: $($_.Exception.Message)"
}
