package kr.ac.kaist.ecl.mjo

import java.io.File
import java.util.function.Consumer


class ProcessWatchDog {

	private var watchList: MutableSet<String> = mutableSetOf()
	private var callbackList: MutableMap<String, Consumer<ProcessHandle>> = mutableMapOf()

	val ProcessHandle.name: String
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
		while (true) {
			ProcessHandle
					.allProcesses()
					.filter(ProcessHandle::isAlive)
					.filter { it.name in notYetRegistered }
					.peek {println("${it.name} has started")}
					.peek { it.onExit().thenAcceptAsync(callbackList[it.name]) }
					.forEach { notYetRegistered.remove(it.name) }
		}
	}
}
