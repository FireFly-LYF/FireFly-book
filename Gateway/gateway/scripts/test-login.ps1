$body = @{ tenant = "tenant-a" } | ConvertTo-Json -Compress
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/gateway/login" `
    -ContentType "application/json" `
    -Body $body
