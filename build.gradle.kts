plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "me.lukiiy"
description = "A NMS library made to create a custom locator bar."
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    paperweight.paperDevBundle("1.21.6-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}