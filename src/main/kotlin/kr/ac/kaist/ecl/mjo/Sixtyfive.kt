package kr.ac.kaist.ecl.mjo

import com.dropbox.core.DbxAppInfo
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.DbxWebAuth
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.*
import java.net.URI
import java.nio.file.Path

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
		.let(Json.Default::decodeFromString)
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

		watchList.parallelStream().forEach(this::sync)
	}

	private inline val String.toZipName: String get() = this.replace(Regex("exe$"), "zip")
	private inline val String.toUploadZipName: String get() = "/data/${this.toZipName}"

	private fun sync(processName: String) {
		val localModifiedTime = config[processName]?.lastModified?.get(hostName)
		val (remoteData, uploadedTime) = when (val pair = downloadAppData(processName)) {
			null -> {
				logger.warn("Failed to download $processName from dropbox")
				return
			}
			else -> pair
		}
		if (localModifiedTime != null) {
			when (localModifiedTime.compareTo(uploadedTime)) {
				in 1 until Int.MAX_VALUE -> restore(processName)
				in -1 downTo Int.MIN_VALUE -> backup(processName)
				0 -> logger.info("$processName has not changed")
			}
		} else {
			val localPath = config[processName]?.savePath?.toZipName
			if (localPath?.let(Path::of)?.toFile()?.exists() == true) {
				val backupPath = "${localPath}.bak.zip"
				logger.info("Local data exist. Save backup at $backupPath.")
				val writer = FileWriter(backupPath)
				writer.write(pack(localPath).readAllBytes().toString())
				writer.close()
			} else {
				logger.info("${processName.toZipName} does not exist in local. Download from remote")
				restore(processName, remoteData, uploadedTime)
			}
		}
	}

	fun watchProcesses() = watchDog.start()

	private fun downloadAppData(processName: String): Pair<ByteArrayInputStream, Long>? = ByteArrayOutputStream()
		.runCatching {
			val metadata = dropbox.files().downloadBuilder(processName.toUploadZipName).download(this)
			this to metadata
		}.mapCatching { it.first.toByteArray().inputStream() to it.second }
		.mapCatching { it.first to it.second.serverModified.time }
		.getOrNull()

	fun restore(processName: String) {
		when (val pair = downloadAppData(processName)) {
			null -> {
				logger.warn("Failed to download $processName from dropbox")
				return
			}
			else -> {
				logger.debug("Downloaded ${processName.toZipName} from dropbox")
				restore(processName, pair.first, pair.second)
			}
		}
	}

	private fun restore(processName: String, remoteData: InputStream, uploadedTime: Long) {
		config[processName]
			?.savePath
			?.expand
			?.also { remoteData.let { data -> unpack(data, it) } }
			?.also { config[processName]!!.lastModified[hostName] = uploadedTime }
			?.also { updateConfig() }
			?.run { logger.info("$processName is successfully restored") }
			?: logger.warn("$processName does not exists")
	}

	fun backup(processName: String) {
		val localData = config[processName]
			?.savePath
			?.expand
			?.let(::pack)

		val metadata = dropbox.files()
			.uploadBuilder(processName.toUploadZipName)
			.withMode(WriteMode.OVERWRITE)
			.uploadAndFinish(localData)

		config[processName] = metadata.serverModified.time
		updateConfig()

		logger.info("$processName is backed up")
	}

	fun addConfig(processName: String, path: String) {
		logger.info("Process: $processName  path: $path")
		config[processName] = AppConfig(processName, path, mutableMapOf())
		updateConfig()
		logger.info("Configuration is updated")
	}

	private fun updateConfig() {
		dropbox
			.files()
			.uploadBuilder(uploadConfigName)
			.withMode(WriteMode.OVERWRITE)
			.uploadAndFinish(Json.encodeToString(config).byteInputStream())
	}

	fun removeConfig(processName: String) {
		config[processName]
			?.also { config.applications.remove(it) }
			?.also { updateConfig() }
			?.also { logger.info("$it is successfully popped") }
			?: logger.warn("$processName does not exist")
	}

}

