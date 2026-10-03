FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -Dmaven.test.skip=true package

FROM alpine:3.23 AS media-probe
RUN apk add --no-cache build-base curl xz linux-headers
WORKDIR /probe
RUN curl --fail --location https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz -o source.tar.xz \
    && echo "8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e  source.tar.xz" | sha256sum -c - \
    && tar -xf source.tar.xz
WORKDIR /probe/ffmpeg-9.0.2
RUN ./configure --disable-everything --disable-autodetect --disable-doc --disable-debug \
    --disable-network --disable-ffplay --enable-ffprobe --enable-protocol=file \
    --enable-demuxer=mov --enable-decoder=h264,aac --enable-parser=h264,aac \
    --enable-small --disable-asm --extra-ldflags=-static \
    && make -j2 ffprobe

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
COPY --from=media-probe /probe/ffmpeg-9.0.2/ffprobe /usr/local/bin/ffprobe
COPY --from=media-probe /probe/ffmpeg-9.0.2/COPYING.LGPLv2.1 /usr/local/share/licenses/ffprobe-LGPLv2.1.txt
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/snowboard-v2-backend-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
