package kr.sixtyfive

import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.function.Consumer



class ProcessWatchDog {

	private val logger = LoggerFactory.getLogger(this::class.java)

	private val notYetRegisteredProcessList = CopyOnWriteArraySet<String>()
	private val callbackMap = ConcurrentHashMap<String, Consumer<String>>()
	private inline val ProcessHandle.name: String get() = this.info().command().orElse("").let { File(it).name }


	fun register(processName: String, onExitCallBack: Consumer<String>) {
		notYetRegisteredProcessList.add(processName)
		callbackMap[processName] = onExitCallBack
	}

	fun start() {
		logger.info("$notYetRegisteredProcessList")
		startImpl()
	}

	private tailrec fun startImpl() {
		ProcessHandle.allProcesses()
			.filter(ProcessHandle::isAlive)
			.filter { it.name in notYetRegisteredProcessList }
			.forEach {
				val name = it.name
				logger.info("$name has started")
				notYetRegisteredProcessList.remove(name)
				it.onExit().thenAccept { callbackMap[name]!!.accept(name) }
			}
		Thread.sleep(1000)
		startImpl()
	}
}
