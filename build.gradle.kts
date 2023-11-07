plugins {
	kotlin("jvm") version "1.9.20"
	id("org.beryx.runtime") version "1.12.7"

	application
}
group = "kr.sixtyfive"
version = "1.0"

repositories {
	mavenCentral()
	maven { url = "https://jitpack.io".let(::uri) }
}
dependencies {
	implementation("org.codehaus.httpcache4j.uribuilder:uribuilder:2.0.0")
	implementation("com.google.code.gson:gson:2.10.1")

	implementation("com.github.snowphone:async-dropbox:0.3.1")
	implementation("com.github.snowphone:cjk-table:0.3")

	implementation("org.jsoup:jsoup:1.15.4")

	implementation("org.zeroturnaround:zt-zip:1.15")

	implementation("ch.qos.logback:logback-classic:1.4.7")
	implementation("org.slf4j:slf4j-api:2.0.5")

	implementation("com.github.ajalt.clikt:clikt:3.5.2")


	testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.8.10")
}

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_1_8
}

application {
	// Define the main class for the application.
	mainClass.set("$group.MainKt")
	// Tell jvm that runs on UTF-8 environment
	// For more information, go to https://docs.gradle.org/current/userguide/application_plugin.html#configureApplicationDefaultJvmArgs
	applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Dname=${rootProject.name}")
}

runtime {
	options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
	jpackage {
		//icon = "build/resources/main/icon.ico"
		imageOptions = listOf(
			"--win-console",
			//"--resource-dir", "build/resources/main",
			"--icon", "src/main/resources/icon.ico"
		)
	}
}