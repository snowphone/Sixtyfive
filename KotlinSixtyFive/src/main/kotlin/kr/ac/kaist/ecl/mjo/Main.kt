package kr.ac.kaist.ecl.mjo

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kotlin.system.exitProcess

class Main: CliktCommand(name = "Sixtyfive") {
	private val list by option("-l", "--list", help = "Print configuration and exit").flag(default = false)
	private val path by option("-p", "--path", metavar = "processName", help = "Print given process' expanded path and exit")
	private val upload by option("-u", "--upload", metavar = "processName", help = "Upload a given process' data into cloud")
	private val download by option("-d", "--download", metavar = "processName", help = "Download a given process' data from cloud")
	private val add by option("-a", "--add", metavar = "processName=Path").associate()
	private val remove by option(metavar = "processName")

	private val String?.neitherNullNorEmpty: Boolean
		get() = this?.isNotEmpty() ?: false

	override fun run() {
		val sixtyfive = Sixtyfive()
		var needWatching = true
		if(add.isNotEmpty()) {
			add.entries
				.first()
				.let { sixtyfive.addConfig(it.key, it.value) }
			needWatching = false
		}
		if(remove.neitherNullNorEmpty) {
			remove?.let { sixtyfive.removeConfig(it) }
			needWatching = false
		}
		if (list) {
			val len = sixtyfive
				.config
				.applications
				.map(AppConfig::name)
				.maxOf(String::length)
			sixtyfive
				.config
				.applications
				.forEach { println("${it.name.padEnd(len)}: ${it.savePath}") }
			exitProcess(0)
		}
		if (path.neitherNullNorEmpty) {
			sixtyfive
				.config.applications.filter { it.name == path}
				.firstOrNull()
				?.savePath
				?.let(sixtyfive::expandPath)
				?.let { println("$path: $it") }
			needWatching = false
		}
		if(upload.neitherNullNorEmpty) {
			upload?.let(sixtyfive::backup)
			needWatching = false
		}
		if(download.neitherNullNorEmpty) {
			download?.let(sixtyfive::restore)
			needWatching = false
		}

		if (needWatching) {
			sixtyfive.watchProcesses()
		}
	}
}
//TODO("Apply GUI form")
fun main(args: Array<String>) = Main().main(args)
