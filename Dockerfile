FROM openjdk:8
RUN mkdir /app && \
    cd /app && \
    curl -OL https://github.com/MiradoConsulting/RobocodeEngine/releases/download/1.0.0-SNAPSHOT/robocodeengine-1.0.0-SNAPSHOT.jar
COPY libs /app/libs
WORKDIR /app
COPY src/main/resources/config.yaml /app/config.yaml
COPY start.sh /app/start.sh
RUN chmod +x start.sh
CMD /app/start.sh
EXPOSE 8080
