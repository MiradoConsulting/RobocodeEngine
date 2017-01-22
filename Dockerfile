FROM openjdk:8
RUN mkdir /app && \
    cd /app
COPY libs /app/libs
COPY robocodeengine-1.0.0-SNAPSHOT.jar /app/robocodeengine-1.0.0-SNAPSHOT.jar
WORKDIR /app
COPY src/main/resources/config.yaml /app/config.yaml
COPY start.sh /app/start.sh
RUN chmod +x start.sh
CMD /app/start.sh
EXPOSE 8080
