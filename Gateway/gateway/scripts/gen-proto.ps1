$Root = (Split-Path -Parent $PSScriptRoot)
Set-Location $Root
protoc `
  --proto_path=api/proto `
  --go_out=internal/grpcserver/pb --go_opt=paths=source_relative `
  --go-grpc_out=internal/grpcserver/pb --go-grpc_opt=paths=source_relative `
  api/proto/greeter.proto
Write-Host "generated internal/grpcserver/pb/"