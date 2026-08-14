# 运维登录（独立于业务 Access）
# 口令见 gateway.yaml admin.password
Invoke-RestMethod -Method POST -Uri "http://localhost:8080/gateway/login" `
  -ContentType "application/json" `
  -Body '{"tenant":"tenant-a","password":"admin-dev-change-me"}'
