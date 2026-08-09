plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
}

tasks.shadowJar {
    archiveBaseName.set("viGeyserUpdater-Velocity")
    archiveClassifier.set("")
    relocate("com.google.gson", "dev.visherryz.vigeyserupdater.lib.gson")
    relocate("org.yaml.snakeyaml", "dev.visherryz.vigeyserupdater.lib.snakeyaml")
}

tasks.build { dependsOn(tasks.shadowJar) }
