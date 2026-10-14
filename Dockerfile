# Etapa 1: compilar con el wrapper de Gradle (JDK completo)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
# Capa de caché: las dependencias solo se descargan de nuevo si cambia el build
RUN ./gradlew dependencies --no-daemon -q
COPY src ./src
RUN ./gradlew bootJar --no-daemon -q -x test

# Etapa 2: ejecutar (imagen liviana, solo JRE)
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
