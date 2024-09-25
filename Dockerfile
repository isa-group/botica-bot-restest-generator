FROM openjdk:11

WORKDIR /app
COPY target/bot.jar /app/

CMD ["java", "-jar", "/app/bot.jar"]
