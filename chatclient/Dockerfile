FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package dependency:copy-dependencies -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb \
    x11vnc \
    novnc \
    websockify \
    fluxbox \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s /usr/share/novnc/vnc.html /usr/share/novnc/index.html

WORKDIR /app
COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/dependency ./dependency

ENV DISPLAY=:99
EXPOSE 6080

CMD ["bash", "-c", "Xvfb :99 -screen 0 800x600x24 & sleep 1 && fluxbox & x11vnc -display :99 -nopw -forever -shared -bg && websockify --web=/usr/share/novnc/ 6080 localhost:5900 & exec java -cp 'classes:dependency/*' com.chatclient.ClientGUI"]
