package kr.sixtyfive

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.default
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
	).default("")
	private val upload by option(
		"-u",
		"--upload",
		metavar = "PROC",
		help = "Upload a given process' data into cloud"
	).default("")
	private val download by option(
		"-d",
		"--download",
		metavar = "PROC",
		help = "Download a given process' data from cloud"
	).default("")
	private val add by option(
		"-a",
		"--add",
		metavar = "PROC=PATH",
		help = "Append the item pair into configuration"
	).associate()
	private val remove by option(metavar = "PROC", help = "Remove the item from configuration").default("")


	override fun run() {
		val sixtyfive = Sixtyfive()
		when {
			add.isNotEmpty() -> add
				.entries
				.first()
				.let { sixtyfive.addConfig(it.key, it.value) }
				.join()

			remove.isNotEmpty() -> remove
				.let(sixtyfive::removeConfig)
				.join()

			list -> sixtyfive
				.config
				.toString()
				.let(::println)

			path.isNotEmpty() -> sixtyfive
				.config.applications.firstOrNull { it.name == path }
				?.save_path
				?.expand
				?.let { logger.info("$path: $it") }

			upload.isNotEmpty() -> upload.let(sixtyfive::backup).join()

			download.isNotEmpty() -> download.let(sixtyfive::restore).join()

			else -> sixtyfive.also { it.syncAll(); it.watchProcesses() }
		}
	}
}

//TODO("Apply GUI form")
fun main(args: Array<String>) = Main().main(args)
