package kr.ac.kaist.ecl.mjo

import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import com.google.gson.Gson
import kr.ac.kaist.ecl.mjo.Zip.pack
import kr.ac.kaist.ecl.mjo.Zip.unpack
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.file.Paths

class Sixtyfive(configName: String = "configs.json") {
	private val watchDog = ProcessWatchDog()
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
		.let { Gson().fromJson(it, Config::class.java) }
	private val watchList: List<String> = config
		.applications
		.map(AppConfig::name)
	private val steamDirectory: String
		get() = Runtime
			.getRuntime()
			.exec("""reg query HKEY_CURRENT_USER\SOFTWARE\Valve\Steam /v SteamPath""")
			.let(Process::getInputStream)
			.let(InputStream::bufferedReader)
			.let(BufferedReader::readText)
			.let { Regex("(?<=REG_SZ).*").find(it)!!.value.trim() }
			.let { Paths.get(it, "steamapps", "common").toString() }

	init {
		println("Signed-in user: ${dropbox.users().currentAccount.name.displayName}")

		envs["STEAM"] = steamDirectory
		watchList.stream()
			.peek { watchDog.register(it) { _ -> backup(it) } }
			.peek(this::sync)
	}

	val String.toZipName: String
		get() = this.replace(Regex("exe$"), "zip")
	val String.toUploadZipName: String
		get() = "/data/${this.toZipName}"

	fun watchProcesses() = watchDog.start()
	private fun sync(processName: String): Nothing = TODO()

	fun restore(processName: String) {
		val stream = ByteArrayOutputStream()
			.also { dropbox.files().downloadBuilder(processName.toUploadZipName).download(it) }
			.toByteArray()
			.inputStream()

		val savePath = config
			.applications
			.first { it.name == processName }
			.savePath
			.let(this::expandPath)

		unpack(stream, savePath)
		println("$processName is successfully restored")
	}

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
			.uploadAndFinish(Gson().toJson(config).byteInputStream())
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

