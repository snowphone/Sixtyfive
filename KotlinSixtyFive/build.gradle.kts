import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.4.10"
	id("org.beryx.jlink") version "2.22.3"

	application
}
group = "kr.ac.kaist.ecl.mjo"
version = "1.0"

repositories {
	jcenter()
}
dependencies {
	implementation("org.codehaus.httpcache4j.uribuilder:uribuilder:2.0.0")
	implementation("com.google.code.gson:gson:2.8.6")
	implementation("org.jsoup:jsoup:1.13.1")

	implementation("com.dropbox.core:dropbox-core-sdk:3.1.5")

	implementation("org.zeroturnaround:zt-zip:1.14")
	implementation("org.slf4j:slf4j-simple:2.0.0-alpha1")

	implementation("com.github.ajalt.clikt:clikt:3.0.1")

	testImplementation(kotlin("test-junit"))
}
tasks.withType<KotlinCompile> {
	kotlinOptions.jvmTarget = "1.8"
}

val runtimeArgs = listOf("-Dfile.encoding=UTF-8", "-Dname=${rootProject.name}")

application {
	// Define the main class for the application.
	mainClass.set("$group.MainKt")
	// Tell jvm that runs on UTF-8 environment
	// For more information, go to https://docs.gradle.org/current/userguide/application_plugin.html#configureApplicationDefaultJvmArgs
	applicationDefaultJvmArgs = runtimeArgs
	applicationName = rootProject.name
}

jlink {
	options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
	launcher {
		jvmArgs = runtimeArgs
	}
	jpackage {
		icon = "build/resources/main/icon.ico"
	}
}

java {
	modularity.inferModulePath.set(true)
}

tasks.withType<JavaCompile> {
	doFirst {
		options.compilerArgs = listOf( "--module-path", classpath.asPath )
		classpath = files()
	}
}
