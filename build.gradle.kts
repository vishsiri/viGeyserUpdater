plugins {
    java
    id("com.gradleup.shadow") version "8.3.8" apply false
}

allprojects {
    group = "dev.visherryz.vigeyserupdater"
    version = "1.1.0-SNAPSHOT"
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

subprojects {
    apply(plugin = "java")
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
