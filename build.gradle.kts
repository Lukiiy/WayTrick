plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "me.lukiiy"
description = "A NMS library made to create a custom locator bar."
version = "1.0"

repositories {
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.6-R0.1-SNAPSHOT")
}