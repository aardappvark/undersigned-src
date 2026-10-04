# The Undersigned API as a container, for a public host. The build itself runs on the developer's machine (the Gradle
# project includes the Android app, which needs the Android SDK): run `./gradlew :api:installDist` first, then build this
# image from the repository root:  docker build -f api/Dockerfile -t undersigned-api .
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY api/build/install/api /app
# Serving the public: test prices and localhost webhooks are off, and the client address is read from the proxy.
ENV HOST=0.0.0.0 PORT=8080 UNDERSIGNED_PUBLIC=1 UNDERSIGNED_DB=/data/undersigned-api.db
# Set at deploy time: UNDERSIGNED_RPC (keyed RPC first, comma-separated), PUBLIC_URL (https://your.host),
# UNDERSIGNED_CONTACT (an address for content reports), TRUST_PROXY=1 when behind a proxy that sets X-Forwarded-For.
VOLUME /data
EXPOSE 8080
CMD ["/app/bin/api"]
