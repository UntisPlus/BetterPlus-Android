rootProject.name = "BetterUntis"

pluginManagement {
	repositories {
		gradlePluginPortal()
		google()
		mavenCentral()
	}
}

dependencyResolutionManagement {
	repositories {
		google()
		mavenCentral()
		mavenLocal()
	}
}

plugins {
	id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":app")
include(":material-color-utils")
include(":api")
