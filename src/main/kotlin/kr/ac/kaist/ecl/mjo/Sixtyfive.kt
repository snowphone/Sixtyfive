package kr.ac.kaist.ecl.mjo

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kr.ac.kaist.ecl.mjo.dropbox.AppKey
import kr.ac.kaist.ecl.mjo.dropbox.Dropbox
import kr.ac.kaist.ecl.mjo.dropbox.Response
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.io.InputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

// TODO: 코드 리팩토링하기
class Sixtyfive(configName: String = "configs.json") {
	private val logger = LoggerFactory.getLogger(this::class.java)

	private val uploadConfigName = configName
	private val dropbox: Dropbox = accessDropbox()
	val config: Config = dropbox
		.download(uploadConfigName)
		.get()
		.first
		.bufferedReader()
		.readText()
		.apply(logger::debug)
		.let(Json::decodeFromString)
	private val watchList: List<String> = config
		.applications
		.map(AppConfig::name)
	private val watchDog = ProcessWatchDog()


	private fun accessDropbox(): Dropbox {
		val info = this::class.java
			.getResource("/key.json")
			.readText()
			.let<String, AppKey>(Json::decodeFromString)

		return Dropbox(info.key, info.secret)
	}


	init {
		logger.info("Signed-in user: ${dropbox.user}")

		watchList.forEach {
			watchDog.register(it) { _ ->
				logger.info("$it has terminated")
				backup(it).join()
			}
		}

		watchList.map(this::sync).run { CompletableFuture.allOf(*toTypedArray()).join() }
	}

	private inline val String.toZipName: String get() = this.replace(Regex("exe$"), "zip")
	private inline val String.toUploadZipName: String get() = "data/${this.toZipName}"

	private fun sync(processName: String): CompletableFuture<Void>? {
		val localModifiedTime = config[processName]?.lastModified?.get(hostName)

		return downloadAppData(processName)
			.thenComposeAsync {
				when (it) {
					null -> {
						logger.warn("Failed to download $processName from dropbox")
						null
					}
					else -> {
						logger.debug("$processName is well downloaded")
						completedFuture(it)    // remote data, uploadedTime
					}
				}
			}?.thenComposeAsync { (remoteData, uploadedTime) ->
				if (localModifiedTime != null) {
					when (localModifiedTime.compareTo(uploadedTime)) {
						in 1 until Int.MAX_VALUE -> restore(processName)
						in -1 downTo Int.MIN_VALUE -> backup(processName)
						else -> {
							logger.info("$processName has not changed")
							completedFuture(null)
						}
					}
				} else {
					val localPath = config[processName]?.savePath?.toZipName
					if (localPath?.let(Path::of)?.toFile()?.exists() == true) {
						val backupPath = "${localPath}.bak.zip"
						logger.info("Local data exist. Save backup at $backupPath.")
						FileWriter(backupPath).use {
							it.write(pack(localPath).readAllBytes().toString())
						}
						completedFuture(null)
					} else {
						logger.info("${processName.toZipName} does not exist in local. Download from remote")
						restore(processName, remoteData, uploadedTime)
					}

				}
			}
	}

	fun watchProcesses() = watchDog.start()

	private fun downloadAppData(processName: String): CompletableFuture<Pair<InputStream, Long>?> {
		return dropbox.download(processName.toUploadZipName)
			.thenApply { (data, resp) ->
				resp?.server_modified?.time?.let { data to it }
			}
	}

	private val String.time: Long
		get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
			.parse(this)
			.time

	fun restore(processName: String): CompletableFuture<Void> {
		return downloadAppData(processName)
			.thenComposeAsync {
				when (it) {
					null -> {
						logger.warn("Failed to download $processName from dropbox")
						completedFuture(null)
					}
					else -> {
						logger.debug("Downloaded ${processName.toZipName} from dropbox")
						restore(processName, it.first, it.second)
					}
				}
			}
	}

	private fun restore(processName: String, remoteData: InputStream, uploadedTime: Long): CompletableFuture<Void> {
		val conf = config[processName]

		return conf?.savePath?.expand
			?.also { unpack(remoteData, it) }
			?.also { conf.lastModified[hostName] = uploadedTime }
			?.run { updateConfig() }
			?.thenAccept { logger.info("$processName is successfully restored") }
			?: run {
				logger.warn("$processName does not exists")
				completedFuture(null)
			}
	}

	fun backup(processName: String): CompletableFuture<Void> {
		val localData = config[processName]
			?.savePath
			?.expand
			?.let(::pack)

		val metadata = localData
			?.let { dropbox.upload(it, processName.toUploadZipName) }

		metadata?.get()?.server_modified?.time
			?.let { config[processName] = it }

		return updateConfig().thenAccept { logger.info("$processName has backed up") }
	}

	fun addConfig(processName: String, path: String): CompletableFuture<Void> {
		logger.info("Process: $processName  path: $path")
		config[processName] = AppConfig(processName, path, mutableMapOf())
		return updateConfig()
			.thenAccept { logger.info("Configuration is updated") }
	}

	private fun updateConfig(): CompletableFuture<Response?> {
		return dropbox.upload(Json.encodeToString(config).byteInputStream(), uploadConfigName)
	}

	fun removeConfig(processName: String): CompletableFuture<Void> = config[processName]
		?.also { config.applications.remove(it) }
		?.run { updateConfig().thenAccept { logger.info("$this is successfully popped") } }
		?: run {
			logger.warn("$processName does not exist")
			completedFuture(null)
		}

}

