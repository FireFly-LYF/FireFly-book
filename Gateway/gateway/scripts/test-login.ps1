# 运维登录（独立于业务 Access）
# 口令来自环境变量 ADMIN_PASSWORD（由 start-all / .env 注入）
if (-not $env:ADMIN_PASSWORD) {
  Write-Error "ADMIN_PASSWORD is required (load .env first)"
  exit 1
}
$body = @{ tenant = "tenant-a"; password = $env:ADMIN_PASSWORD } | ConvertTo-Json
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/gateway/login" `
  -ContentType "application/json" `
  -Body $body
