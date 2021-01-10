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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

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
	private inline val String.toZipName: String get() = this.replace(Regex("exe$"), "zip")
	private inline val String.toUploadZipName: String get() = "data/${this.toZipName}"
	private inline val String.time: Long
		get() = ZonedDateTime
			.parse(this, DateTimeFormatter.ISO_DATE_TIME)
			.toInstant()
			.toEpochMilli()

	init {
		logger.info("Signed-in user: ${dropbox.user}")

		watchList.forEach {
			watchDog.register(it) { _ ->
				logger.info("$it has terminated")
				backup(it).join()
			}
		}
		syncAll()
	}

	private fun accessDropbox(): Dropbox {
		val tokenPath = "%LOCALAPPDATA%/Sixtyfive/token.txt".expand
		val info = this::class.java
			.getResource("/key.json")
			.readText()
			.let<String, AppKey>(Json::decodeFromString)

		return Dropbox(info.key, info.secret, tokenPath)
	}

	private fun syncAll() = watchList.map(this::sync).run { CompletableFuture.allOf(*toTypedArray()).join() }

	private fun sync(processName: String): CompletableFuture<Void>? {
		val localModifiedTime = config[processName]?.lastModified?.get(hostName)

		return downloadAppData(processName)
			.thenCompose {
				when (it) {
					null -> {
						logger.warn("Failed to download $processName from dropbox")
						null
					}
					else -> completedFuture(it)
				}
			}?.thenCompose { (remoteData, uploadedTime) ->
				if (localModifiedTime != null) {
					when {
						localModifiedTime < uploadedTime -> backup(processName)
						localModifiedTime > uploadedTime -> restore(processName)
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

	private fun downloadAppData(processName: String): CompletableFuture<Pair<InputStream, Long>?> {
		return dropbox.download(processName.toUploadZipName)
			.thenApply { (data, resp) ->
				resp?.server_modified?.time?.let { data to it }
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

	fun removeConfig(processName: String): CompletableFuture<Void> = config[processName]
		?.also { config.applications.remove(it) }
		?.run { updateConfig().thenAccept { logger.info("$this is successfully popped") } }
		?: run {
			logger.warn("$processName does not exist")
			completedFuture(null)
		}

	private fun updateConfig(): CompletableFuture<Response?> {
		return dropbox.upload(Json.encodeToString(config).byteInputStream(), uploadConfigName)
	}
}

