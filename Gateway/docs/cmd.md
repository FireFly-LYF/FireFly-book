*动态注册/下线*
curl.exe http://localhost:8080/gateway/services

curl.exe -X POST http://localhost:8080/gateway/services -H "Content-Type: application/json" -d "{\"url\":\"http://localhost:9004\"}"

curl.exe -X DELETE http://localhost:8080/gateway/services -H "Content-Type: application/json" -d "{\"url\":\"http://localhost:9004\"}"

*先登录再请求*
@echo off
for /f "delims=" %%t in ('curl.exe -s -X POST http://localhost:8080/gateway/login -H "Content-Type: application/json" -d "{\"tenant\":\"tenant-a\"}"') do set resp=%%t

set token=粘贴login返回的token

for /L %%i in (1,1,5) do curl.exe -s -H "Authorization: Bearer %token%" http://localhost:8080/api/user


*redis*
docker run -d --name redis -p 6379:6379 redis:7-alpine
docker start redis
docker exec -it redis redis-cli ping

*grpcurl*
grpcurl -plaintext `
  -proto api/proto/greeter.proto `
  -d '{}' `
  localhost:50052 gateway.UserService/Greet

grpcurl -plaintext -d "{\"name\":\"Alice\"}" localhost:50051 gateway.UserService/Greet

*mysql*
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=123456 -p 3306:3306 mysql:8.0
docker exec -it mysql mysql -uroot -p123456