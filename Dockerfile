FROM eclipse-temurin:17-jre-alpine

ENV TZ="Asia/Shanghai"

RUN apk add --no-cache font-noto-cjk wget && \
    rm -rf /var/cache/apk/*

WORKDIR /app

COPY NyxBot.jar /app

VOLUME [ "/app" ]

EXPOSE 8080

CMD ["java", "-Dfile.encoding=UTF-8","-jar", "/app/NyxBot.jar"]