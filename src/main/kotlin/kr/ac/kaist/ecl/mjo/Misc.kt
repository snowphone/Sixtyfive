package kr.ac.kaist.ecl.mjo

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
val String.expand: String
	get() = this.replace(Regex("%(.*?)%")) { envs[it.groupValues[1]]!! }


val steamDirectory = Runtime
	.getRuntime()
	.exec("""reg query HKEY_CURRENT_USER\SOFTWARE\Valve\Steam /v SteamPath""")
	.let(Process::getInputStream)
	.let(InputStream::bufferedReader)
	.let(BufferedReader::readText)
	.let { Regex("(?<=REG_SZ).*").find(it)!!.value.trim() }
	.let { Paths.get(it, "steamapps", "common").toString() }

private val envs = System.getenv().toMutableMap()
	.also {
		it["STEAM"] = steamDirectory
	}


fun pack(rootDir: String): InputStream = ByteArrayOutputStream()
	.also { ZipUtil.pack(File(rootDir), it) }
	.toByteArray()
	.inputStream()

fun unpack(inputStream: InputStream, dst: String) {
	ZipUtil.unpack(inputStream, File(dst))
}

val hostName: String
	get() = InetAddress.getLocalHost().hostName!!
