*创建项目*

curl.exe -o user.zip "https://start.spring.io/starter.zip?type=maven-project&language=java&bootVersion=4.1.0&baseDir=user-service&groupId=com.firefly&artifactId=user-service&name=user-service&packageName=userservice&javaVersion=17&dependencies=web"

Expand-Archive -Path user.zip -DestinationPath . -Force
Remove-Item user.zip



*快速编译运行（必须在 hello-service 目录下）*

cd d:\A_Software\Java\SAVE\FireFly-book\services\hello-service
mvn spring-boot:run

*mysql*
docker run -d --name mysql -e MYSQL_ROOT_PASSWORD=123456 -p 3306:3306 mysql:8.0
docker exec -it mysql mysql -uroot -p123456