package kr.sixtyfive

import org.slf4j.LoggerFactory
import org.zeroturnaround.zip.ZipUtil
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.nio.file.Paths

/**
 * Expands environment variables to their corresponding value.
 * It also expands %STEAM%, pointing "<STEAM>/steamapps/common".
 */
val String.expand: String get() = this.replace(Regex("%(.*?)%")) { envs[it.groupValues[1]]!! }


val steamDirectory = Runtime
	.getRuntime()
	.exec("""reg query HKEY_CURRENT_USER\SOFTWARE\Valve\Steam /v SteamPath""")
	.let(Process::getInputStream)
	.let(InputStream::bufferedReader)
	.let(BufferedReader::readText)
	.let { Regex("(?<=REG_SZ).*").find(it)?.value?.trim() ?: "C:\\Program Files (x86)\\Steam" }
	.let { Paths.get(it, "steamapps", "common").toString() }

private val envs = System.getenv().toMutableMap()
	.also { it["STEAM"] = steamDirectory }


fun pack(rootDir: String): InputStream = ByteArrayOutputStream()
	.also { ZipUtil.pack(File(rootDir), it) }
	.toByteArray()
	.inputStream()

fun packEntry(rootPath: String): InputStream = ZipUtil.packEntry(File(rootPath)).inputStream()

fun unpack(inputStream: InputStream, dst: String, isFolder: Boolean): Boolean? {
	val logger = LoggerFactory.getLogger("Unpacker")
	return logger.runCatching {
		if (isFolder) {
			ZipUtil.unpack(inputStream, File(dst))
			true
		}
		else {
			ZipUtil.unpackEntry(inputStream, File(dst).name, File(dst))
		}
	}.onFailure {
		logger.error("Error occurred while unpacking an archive to $dst.")
		logger.error(it.message)
	}.getOrNull()
}

val hostName: String get() = InetAddress.getLocalHost().hostName!!
