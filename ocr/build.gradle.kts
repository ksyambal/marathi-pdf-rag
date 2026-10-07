plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    implementation(project(":common"))
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("software.amazon.awssdk:s3:2.41.28")
    implementation("software.amazon.awssdk:textract:2.41.28")
    implementation("org.slf4j:slf4j-simple:2.0.17")
}

tasks.shadowJar {
    archiveFileName.set("ocr-worker.jar")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "com.kapil.marathipdfrag.ocr.OcrWorker"
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
