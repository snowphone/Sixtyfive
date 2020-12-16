package kr.ac.kaist.ecl.mjo

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import org.slf4j.LoggerFactory

class Main : CliktCommand(name = "Sixtyfive") {
	private val logger = LoggerFactory.getLogger(this::class.java)

	private val list by option("-l", "--list", help = "Print configuration and exit").flag(default = false)
	private val path by option(
		"-p",
		"--path",
		metavar = "PROC",
		help = "Print given process' expanded path and exit"
	)
	private val upload by option(
		"-u",
		"--upload",
		metavar = "PROC",
		help = "Upload a given process' data into cloud"
	)
	private val download by option(
		"-d",
		"--download",
		metavar = "PROC",
		help = "Download a given process' data from cloud"
	)
	private val add by option(
		"-a", 
		"--add", 
		metavar = "PROC=PATH",
		help = "Append the item pair into configuration"
	).associate()
	private val remove by option(metavar = "PROC", help = "Remove the item from configuration")

	private val String?.neitherNullNorEmpty: Boolean
		get() = this?.isNotEmpty() ?: false

	override fun run() {
		val sixtyfive = Sixtyfive()
		when {
			add.isNotEmpty() -> add
				.entries
				.first()
				.let { sixtyfive.addConfig(it.key, it.value) }

			remove.neitherNullNorEmpty -> remove
				?.let(sixtyfive::removeConfig)

			list -> sixtyfive
				.config
				.toString()
				.split('\n')
				.forEach(logger::info)

			path.neitherNullNorEmpty ->
				sixtyfive
					.config.applications.firstOrNull { it.name == path }
					?.savePath
					?.expand
					?.let { logger.info("$path: $it") }

			upload.neitherNullNorEmpty -> upload?.let(sixtyfive::backup)

			download.neitherNullNorEmpty -> download?.let(sixtyfive::restore)

			else -> sixtyfive.also(Sixtyfive::watchProcesses)
		}
	}
}

//TODO("Apply GUI form")
fun main(args: Array<String>) = Main().main(args)
