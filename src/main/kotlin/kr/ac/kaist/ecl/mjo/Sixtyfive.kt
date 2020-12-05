package kr.ac.kaist.ecl.mjo

import com.dropbox.core.DbxAppInfo
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.DbxWebAuth
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.*
import java.net.URI

@Serializable
data class Key(val key: String, val secret: String)

class Sixtyfive(configName: String = "configs.json") {
	private val tokenPath = "%LOCALAPPDATA%/Sixtyfive/token.txt".expand
	private val logger = LoggerFactory.getLogger(this::class.java)

	private val uploadConfigName = "/$configName"
	private val dropbox: DbxClientV2 = accessDropbox()
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


	private fun accessDropbox(): DbxClientV2 {
		val config = DbxRequestConfig.newBuilder(this::class.java.name).build()
		val info = this::class.java
			.getResourceAsStream("/key.json")
			.let(DbxAppInfo.Reader::readFully)

		val token = runCatching { tokenPath.let(::FileReader) }
			.mapCatching(FileReader::readText)
			.mapCatching(String::trim)
			.getOrElse { authenticate(config, info) }

		return DbxClientV2(config, token)
	}

	private fun authenticate(config: DbxRequestConfig, info: DbxAppInfo): String? {
		val auth = DbxWebAuth(config, info)
		val url = DbxWebAuth.newRequestBuilder().withNoRedirect().build().let(auth::authorize)

		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			Desktop.getDesktop().browse(url.let(::URI))
			logger.info("Log in from your browser and copy TOKEN to console")
		} else {
			logger.info("Go to '$url' and enter the access code")
		}
		val accessCode = readLine()?.trim()

		return auth.finishFromCode(accessCode).accessToken
			?.apply { tokenPath.let(::File).parentFile.mkdirs() }
			?.also { tok -> tokenPath.let(::FileWriter).use { w -> w.write(tok) } }!!
	}

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

