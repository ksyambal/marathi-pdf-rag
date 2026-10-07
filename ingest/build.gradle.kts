plugins {
    java
    id("com.gradleup.shadow") version "8.3.6"
}

dependencies {
    implementation(project(":common"))
    implementation("com.amazonaws:aws-lambda-java-core:1.2.3")
    implementation("com.amazonaws:aws-lambda-java-events:3.14.0")
    implementation("software.amazon.awssdk:s3:2.41.28")
    implementation("software.amazon.awssdk:textract:2.41.28")
    implementation("software.amazon.awssdk:dynamodb:2.41.28")
    implementation("software.amazon.awssdk:sfn:2.41.28")
    implementation("software.amazon.awssdk:comprehend:2.41.28")
    implementation("org.slf4j:slf4j-simple:2.0.17")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
}

tasks.shadowJar {
    archiveClassifier.set("lambda")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
