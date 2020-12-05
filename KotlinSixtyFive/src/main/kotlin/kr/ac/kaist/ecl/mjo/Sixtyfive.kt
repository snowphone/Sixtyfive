package kr.ac.kaist.ecl.mjo

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader

class Sixtyfive(configName: String = "configs.json") {
	private val logger = LoggerFactory.getLogger(this::class.java)

	private val uploadConfigName = "/$configName"
	private val accessToken =
		"Jgl5ZIYICzsAAAAAAAAAAdzXB7cP5CdmfMlPZ0dBnmMa0L2mNKWPvVqMLkzBPCaq" //TODO("Get access token from the web")
	private val dropbox: DbxClientV2 = this::class.java
		.getResource("/key.json")
		.path
		.let(DbxRequestConfig::newBuilder)
		.build()
		.let { DbxClientV2(it, accessToken) }
	val config: Config = dropbox.files()
		.downloadBuilder(uploadConfigName)
		.start()
		.inputStream
		.let(::InputStreamReader)
		.let(InputStreamReader::readText)
		.let { Json.decodeFromString(it) }
	private val watchList: List<String> = config
		.applications
		.map(AppConfig::name)
	private val watchDog = ProcessWatchDog()

	init {
		logger.info("Signed-in user: ${dropbox.users().currentAccount.name.displayName}")

		watchList.forEach {
			watchDog.register(it) { _ ->
				logger.info("$it has terminated")
				backup(it)
			}
		}
	}

	private inline val String.toZipName: String
		get() = this.replace(Regex("exe$"), "zip")
	private inline val String.toUploadZipName: String
		get() = "/data/${this.toZipName}"

	fun watchProcesses() = watchDog.start()


	fun restore(processName: String) {
		val remoteData = ByteArrayOutputStream()
			.runCatching {
				dropbox.files().downloadBuilder(processName.toUploadZipName).download(this)
				this
			}.mapCatching(ByteArrayOutputStream::toByteArray)
			.mapCatching(ByteArray::inputStream)
			.also { logger.debug("Downloaded ${processName.toZipName} from dropbox") }
			.getOrElse {
				logger.warn("Failed to download $processName from dropbox")
				null
			}

		config
			.applications
			.firstOrNull { it.name == processName }
			?.savePath
			?.expand
			?.also { remoteData?.let { data -> unpack(data, it) } }
			?.run { logger.info("$processName is successfully restored") }
			?: logger.warn("$processName does not exists")
	}

	fun backup(processName: String) {
		val localData = config
			.applications
			.firstOrNull { it.name == processName }
			?.savePath
			?.expand
			?.let(::pack)

		dropbox.files()
			.uploadBuilder(processName.toUploadZipName)
			.withMode(WriteMode.OVERWRITE)
			.uploadAndFinish(localData)
		logger.info("$processName is backed up")
	}

	fun addConfig(processName: String, path: String) {
		logger.info("Process: $processName  path: $path")
		config.applications.add(AppConfig(processName, path))
		config.applications.sort()
		uploadConfig()
		logger.info("Configuration is updated")
	}

	private fun uploadConfig() {
		dropbox
			.files()
			.uploadBuilder(uploadConfigName)
			.withMode(WriteMode.OVERWRITE)
			.uploadAndFinish(Json.encodeToString(config).byteInputStream())
	}

	fun removeConfig(processName: String) {
		config.applications
			.firstOrNull { it.name == processName }
			?.also { config.applications.remove(it) }
			?.also { uploadConfig() }
			?.also { logger.info("$it is successfully popped") }
			?: logger.warn("$processName does not exist")
	}

}

