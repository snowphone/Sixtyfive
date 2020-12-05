package kr.ac.kaist.ecl.mjo

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.WriteMode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kr.ac.kaist.ecl.mjo.Zip.pack
import kr.ac.kaist.ecl.mjo.Zip.unpack
import java.io.*
import java.nio.file.Paths

class Sixtyfive(configName: String = "configs.json") {
	private val uploadConfigName = "/$configName"
	private val envs = System.getenv().toMutableMap()
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
	private val steamDirectory = Runtime
		.getRuntime()
		.exec("""reg query HKEY_CURRENT_USER\SOFTWARE\Valve\Steam /v SteamPath""")
		.let(Process::getInputStream)
		.let(InputStream::bufferedReader)
		.let(BufferedReader::readText)
		.let { Regex("(?<=REG_SZ).*").find(it)!!.value.trim() }
		.let { Paths.get(it, "steamapps", "common").toString() }
	private val watchDog = ProcessWatchDog()

	init {
		println("Signed-in user: ${dropbox.users().currentAccount.name.displayName}")

		envs["STEAM"] = steamDirectory
		watchList.forEach { watchDog.register(it) { _ -> backup(it) } }
		watchList.parallelStream().forEach(this::sync)
	}

	val String.toZipName: String
		get() = this.replace(Regex("exe$"), "zip")
	val String.toUploadZipName: String
		get() = "/data/${this.toZipName}"

	fun watchProcesses() = watchDog.start()

	private fun sync(processName: String) {
		val (stream, remoteTime) = downloadZip(processName)
			.let { it.first to it.second?.serverModified?.time }

		val localTime = config
			.applications
			.firstOrNull { it.name == processName }
			?.savePath
			?.let(::File)
			?.lastModified()

		when (remoteTime?.let { localTime?.compareTo(it) }) {
			in Int.MIN_VALUE until 0 -> stream?.let { unpackToDestination(processName, it) }
			in 1 until Int.MAX_VALUE -> backup(processName)
			0 -> println("$processName: is identical")
			//null -> println("$processName got a problem")
		}
	}

	fun restore(processName: String) {
		downloadZip(processName)
			.first
			?.let { unpackToDestination(processName, it) }
	}

	private fun downloadZip(processName: String): Pair<ByteArrayInputStream?, FileMetadata?> = ByteArrayOutputStream()
		.runCatching { this to dropbox.files().downloadBuilder(processName.toUploadZipName).download(this) }
		.map { it.first.toByteArray().inputStream() to it.second }
		.getOrElse {
			println("Failed to download $processName from dropbox")
			null to null
		}

	private fun unpackToDestination(processName: String, stream: InputStream) = config
		.applications
		.firstOrNull { it.name == processName }
		?.savePath
		?.let(this::expandPath)
		?.also { unpack(stream, it) }
		?.run { println("$processName is successfully restored") }
		?: println("$processName does not exists")

	fun backup(processName: String) {
		println("$processName has terminated")

		val savePath = config
			.applications
			.first { it.name == processName }
			.savePath
			.let(this::expandPath)

		val uploadData = pack(savePath)

		dropbox.files()
			.uploadBuilder(processName.toUploadZipName)
			.withMode(WriteMode.OVERWRITE)
			.uploadAndFinish(uploadData)
		println("$processName is backed up")
	}

	fun addConfig(processName: String, path: String) {
		println("Process: $processName  path: $path")
		config.applications.add(AppConfig(processName, path))
		config.applications.sort()
		uploadConfig()
		println("Configuration is updated")
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
			?.also { println("$it is successfully popped") }
			?: println("$processName does not exist")
	}

	fun expandPath(path: String): String {
		val regex = Regex("%(.*?)%")
		return path.replace(regex) { envs[it.groupValues[1]]!! }
	}
}

