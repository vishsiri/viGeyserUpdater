plugins {
    `java-library`
}

dependencies {
    api("com.google.code.gson:gson:2.13.1")
    api("org.yaml:snakeyaml:2.4")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
