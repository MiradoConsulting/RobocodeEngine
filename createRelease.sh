mvn clean package
mv target/robocodeengine-1.0.0-SNAPSHOT.jar .
zip -r release.zip libs/ src/main/resources/config.yaml Dockerfile start.sh robocodeengine-1.0.0-SNAPSHOT.jar
mv robocodeengine-1.0.0-SNAPSHOT.jar target/
