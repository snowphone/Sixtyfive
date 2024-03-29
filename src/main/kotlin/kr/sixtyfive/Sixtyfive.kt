package kr.sixtyfive

import com.google.gson.GsonBuilder
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileReader
import java.io.InputStream
import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

class Sixtyfive(configName: String = "configs.json") {
    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val uploadConfigName = configName
    private val dropbox = accessDropbox()
    val config: Config = dropbox
        .download(uploadConfigName)
        .get()
        .first
        .bufferedReader()
        .readText()
        .apply(logger::debug)
        .let { gson.fromJson(it, Config::class.java) }
    private val watchList: List<String> = config
        .applications
        .map(AppConfig::name)
    private val watchDog = ProcessWatchDog()
    private inline val String.toZipName: String get() = this.replace(Regex("exe$"), "zip")
    private inline val String.toUploadZipName: String get() = "data/${this.toZipName}"
    private inline val String.time: Long get() = Instant.from(ISO_DATE_TIME.parse(this)).toEpochMilli()

    init {
        logger.info("Signed-in user: ${dropbox.user}")
        watchList.forEach { watchDog.register(it, this::processEpilogue) }
    }


    private fun accessDropbox(): Dropbox {
        val tokenPath = "%LOCALAPPDATA%/Sixtyfive/token.txt".expand
        return try {
            tokenPath.let(::FileReader)
                .readText()
                .trim()
                .let(::Dropbox)
        } catch (e: Exception) {
            val (key, secret) = this::class.java
                .getResource("/key.json")
                ?.readText()
                .let { gson.fromJson(it, Map::class.java) }
                .apply(::println)
                .let { it["key"]!! as String to it["secret"]!! as String }

            Dropbox(key, secret, tokenPath)
        }
    }

    private fun processEpilogue(procName: String) {
        logger.info("$procName has terminated")
        watchDog.register(procName, this::processEpilogue)
        logger.debug("Re-registered $procName")
        backup(procName).join()
    }

    fun syncAll() = watchList
        .map(this::sync)
        .let { CompletableFuture.allOf(*it.toTypedArray()).thenAccept { updateConfig() }.join() }

    private fun sync(processName: String): CompletableFuture<Void>? {
        val localModifiedTime = config[processName]?.last_modified?.get(hostName)
            ?: let {
                logger.info("$processName hasn't been synchronized")
                getLastModifiedTime(config[processName]?.save_path?.expand!!)
            }

        return downloadAppData(processName)
            ?.thenCompose { (remoteData, uploadedTime) ->
                when {
                    localModifiedTime == null -> {
                        logger.info("${processName.toZipName} does not exist in local. Download from remote")
                        restore(processName, remoteData, uploadedTime)
                    }

                    localModifiedTime < uploadedTime -> restore(processName)
                    localModifiedTime > uploadedTime -> backup(processName)
                    else -> {
                        logger.info("$processName has not changed")
                        completedFuture(null)
                    }
                }
            }
    }

    /**
     * Iterate whole files in a directory and return the latest modified time in epoch-milli granularity.
     */
    private fun getLastModifiedTime(localDirPath: String): Long? {
        return File(localDirPath)
            .walk()
            .asSequence()
            .map { it.lastModified() }
            .maxOrNull()
    }

    fun watchProcesses() = watchDog.start()

    fun restore(processName: String): CompletableFuture<Void> {
        return downloadAppData(processName)
            ?.thenComposeAsync {
                logger.debug("Downloaded ${processName.toZipName} from dropbox")
                restore(processName, it.first, it.second)
            } ?: completedFuture(null)
    }

    private fun downloadAppData(processName: String): CompletableFuture<Pair<InputStream, Long>>? {
        return dropbox.download(processName.toUploadZipName)
            .thenApply { (data, resp) ->
                resp?.server_modified?.time?.let { data to it }
            } ?: let {
            logger.warn("Failed to download $processName from dropbox")
            null
        }
    }

    private fun restore(processName: String, remoteData: InputStream, uploadedTime: Long): CompletableFuture<Void> {
        val conf = config[processName]

        return conf
            ?.let {
               it.save_path.expand to it.is_folder
            }
            ?.also { logger.info("Restore info: $it") }
            ?.let { (path, isFolder) -> unpack(remoteData, path, isFolder) }
            ?.also { conf.last_modified[hostName] = uploadedTime }
            ?.run { updateConfig() }
            ?.thenAccept { logger.info("$processName is successfully restored") }
            ?: run {
                logger.warn("$processName does not exists")
                completedFuture(null)
            }
    }

    fun backup(processName: String): CompletableFuture<Void> {
        val localData = config[processName]
            ?.let {
                it.save_path.expand to it.is_folder
            }
            ?.let { (path, is_folder) ->
                if (is_folder)
                    pack(path)
                else
                    packEntry(path)
            }

        val metadata = localData
            ?.let { dropbox.upload(it, processName.toUploadZipName) }

        return metadata?.get()?.server_modified?.time
            ?.let { config[processName] = it }
            ?.let {
                updateConfig().thenAccept { logger.info("$processName has backed up") }
            } ?: let {
            logger.warn("Failed to backup $processName")
            completedFuture(null)
        }

    }

    fun addConfig(processName: String, path: String): CompletableFuture<Void> {
        logger.info("Process: $processName  path: $path")
        config[processName] = AppConfig(processName, path, mutableMapOf(), File(path.expand).isDirectory)
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
        return config
            .let { gson.toJson(it).byteInputStream() }
            .let { dropbox.upload(it, uploadConfigName) }
            .thenApply {
                logger.debug("Configuration updated at timestamp ${it?.server_modified}")
                it
            }
    }
}

