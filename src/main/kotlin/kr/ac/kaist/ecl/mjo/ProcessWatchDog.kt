package kr.ac.kaist.ecl.mjo

import org.slf4j.LoggerFactory
import java.io.File
import java.util.function.Consumer


class ProcessWatchDog {
	private val logger = LoggerFactory.getLogger(this::class.java)

	private val watchList: MutableSet<String> = mutableSetOf()
	private val callbackList: MutableMap<String, Consumer<ProcessHandle>> = mutableMapOf()

	private val ProcessHandle.name: String
		get() = this.info()
			.command()
			.orElse("")
			.let(::File)
			.let(File::getName)

	fun register(processName: String, onExitCallBack: Consumer<ProcessHandle>) {
		watchList.add(processName)
		callbackList[processName] = onExitCallBack
	}

	fun start() {
		val notYetRegistered = watchList.toMutableSet()
		logger.info("Watching $notYetRegistered")
		while (true) {
			ProcessHandle
				.allProcesses()
				.filter(ProcessHandle::isAlive)
				.filter { it.name in notYetRegistered }
				.peek { logger.info("${it.name} has started") }
				.peek { it.onExit().thenAccept(callbackList[it.name]) }
				.forEach { notYetRegistered.remove(it.name) }
		}
	}
}
