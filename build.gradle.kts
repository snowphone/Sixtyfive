plugins {
	kotlin("jvm") version "1.5.10"
	id("org.beryx.runtime") version "1.12.5"

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
	implementation("com.google.code.gson:gson:2.8.7")

	implementation("com.github.snowphone:async-dropbox:0.3.1")
	implementation("com.github.snowphone:cjk-table:0.3")

	implementation("org.jsoup:jsoup:1.13.1")

	implementation("org.zeroturnaround:zt-zip:1.14")

	implementation("ch.qos.logback:logback-classic:1.2.3")
	implementation("org.slf4j:slf4j-api:1.7.30")

	implementation("com.github.ajalt.clikt:clikt:3.0.1")


	testImplementation(kotlin("test-junit"))
}
tasks.compileKotlin {
	this.targetCompatibility = "1.8"
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