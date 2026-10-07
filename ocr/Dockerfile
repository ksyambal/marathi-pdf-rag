FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-mar \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY ocr/build/libs/ocr-worker.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
