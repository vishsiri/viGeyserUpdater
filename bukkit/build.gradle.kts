plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}

tasks.shadowJar {
    archiveBaseName.set("viGeyserUpdater-Bukkit")
    archiveClassifier.set("")
    relocate("com.google.gson", "dev.visherryz.vigeyserupdater.lib.gson")
    relocate("org.yaml.snakeyaml", "dev.visherryz.vigeyserupdater.lib.snakeyaml")
}

tasks.build { dependsOn(tasks.shadowJar) }

val pluginVersion: String = version.toString()
tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to pluginVersion) }
}
