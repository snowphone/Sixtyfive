package kr.ac.kaist.ecl.mjo.dropbox

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.ac.kaist.ecl.mjo.expand
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.io.FileReader
import java.io.FileWriter
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpResponse.BodyHandlers
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.CompletableFuture

class Dropbox(key: String, secret: String) {
	private val logger = LoggerFactory.getLogger(this::class.java)
	private val client = Http()
	private val tokenPath = "%LOCALAPPDATA%/Sixtyfive/token.txt".expand
	private val baseHeader: Map<String, String>
		get() = mapOf(
			"Authorization" to "Bearer $token",
			"Content-Type" to "application/octet-stream",
		)

	private var token: String = try {
		FileReader(tokenPath)
			.readText()
			.apply(logger::debug)
			.trim()
	} catch (e: Exception) {
		logger.info("Failed to read token")
		issueToken(key, secret)
	}

	init {
		if (!authenticateToken()) {
			logger.debug("Token is not valid")
			token = issueToken(key, secret)
		}
	}

	val user = "https://api.dropboxapi.com/2/users/get_current_account"
		.let { client.postAsync(it, headers = mapOf("Authorization" to "Bearer $token")) }
		.get()
		.body()
		.let { URLDecoder.decode(it, UTF_8) }
		.let(Json::parseToJsonElement)
		.jsonObject["name"]!!
		.jsonObject["display_name"]!!
		.jsonPrimitive
		.content

	private fun issueToken(key: String, secret: String): String {
		val requestUrl = "https://www.dropbox.com/oauth2/authorize?client_id=$key&response_type=code"
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
			Desktop.getDesktop().browse(URI(requestUrl))
			logger.info("Log in from your browser and copy TOKEN to console")
		} else {
			logger.info("Go to '$requestUrl' and enter the access code")
		}
		val accessCode = readLine()!!.trim()
		val params = mapOf(
			"code" to accessCode,
			"grant_type" to "authorization_code",
			"client_id" to key,
			"client_secret" to secret,
		)
		val token = client.postAsync("https://api.dropboxapi.com/oauth2/token", params = params)
			.get()
			.body()!!
			.let(Json::parseToJsonElement)
			.jsonObject
			.getValue("access_token")
			.jsonPrimitive
			.content

		logger.info("Issued token: $token")
		FileWriter(tokenPath)
			.use { it.write(token) }

		return token
	}


	private fun authenticateToken(): Boolean {
		val url = "https://api.dropboxapi.com/2/users/get_current_account"
		val resp = client.postAsync(url, headers = mapOf("Authorization" to "Bearer $token")).get()

		return resp.statusCode() in 200 until 300
	}


	fun download(fileName: String): CompletableFuture<Pair<InputStream, Response?>> {
		val url = "https://content.dropboxapi.com/2/files/download"
		val params = mapOf("arg" to Json.encodeToString(mapOf("path" to "/$fileName")))

		return client.postAsync(url, headers = baseHeader, params = params, handler = BodyHandlers.ofInputStream())
			.thenApply {
				val metadata = it.headers().firstValue("Dropbox-API-Result")
					.orElse(null)
					?.let<String, Response>(Json::decodeFromString)
				it.body() to metadata
			}
	}

	fun upload(data: InputStream, fileName: String): CompletableFuture<Response?> {
		val headers = baseHeader.toMutableMap()
		//headers["Dropbox-API-Arg"] = URLEncoder.encode(Json.encodeToString(mapOf("path" to "/$fileName")), StandardCharsets.UTF_8)
		headers["mode"] = Json.encodeToString(mapOf(".tag" to "overwrite"))
		val url = "https://content.dropboxapi.com/2/files/upload"
		// Since http header cannot handle non-ascii characters correctly, any
		// characters whose codepoint is bigger than 0x7F should have been escaped.
		// But, both of kotlinx-serialization and Gson do not support this kind
		// of http-header-safe-serialization, I chose to use an alternative: `arg` URL parameter.
		val params = mapOf("arg" to Json.encodeToString(mapOf("path" to "/$fileName")))

		return client.postAsync(url, headers = headers, params = params, data = data)
			.thenApply {
				when (it.statusCode()) {
					in 200 until 300 -> URLDecoder.decode(it.body(), UTF_8)
						.let<String, Response>(Json::decodeFromString)
					else -> null
				}
			}
	}
}

