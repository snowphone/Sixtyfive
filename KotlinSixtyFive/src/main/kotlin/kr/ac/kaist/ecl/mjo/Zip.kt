package kr.ac.kaist.ecl.mjo

import org.zeroturnaround.zip.ZipUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

fun pack(rootDir: String): InputStream = ByteArrayOutputStream()
    .also { ZipUtil.pack(File(rootDir), it) }
    .let { it.toByteArray().inputStream() }

fun unpack(inputStream: InputStream, dst: String) {
    ZipUtil.unpack(inputStream, File(dst))
}