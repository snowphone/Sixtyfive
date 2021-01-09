package kr.ac.kaist.ecl.mjo.dropbox

import org.codehaus.httpcache4j.uri.URIBuilder
import java.io.InputStream
import java.io.InputStream.nullInputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers.ofInputStream
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.CompletableFuture

class Http {
	private val client: HttpClient = HttpClient
		.newBuilder()
		.cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL))
		.build()

	fun postAsync(
		url: String,
		params: Map<String, String> = mapOf(),
		headers: Map<String, String> = mapOf(),
		data: InputStream = nullInputStream(),
	): CompletableFuture<HttpResponse<String>> = postAsync(url, params, headers, data, BodyHandlers.ofString())

	fun <T> postAsync(
		url: String,
		params: Map<String, String> = mapOf(),
		headers: Map<String, String> = mapOf(),
		data: InputStream = nullInputStream(),
		handler: BodyHandler<T>,
	): CompletableFuture<HttpResponse<T>> {
		val request = params.entries
			.fold(URI.create(url)
				.let(URIBuilder::fromURI), { acc, (k, v) ->
				acc.addParameter(k, v)
			}).toURI()
			.let(HttpRequest.newBuilder()::uri)
			.POST(ofInputStream { data })

		headers.forEach { (k, v) -> request.setHeader(k, v) }

		return request.build()
			.let { client.sendAsync(it, handler) }
	}

	fun getAsync(url: String) = getAsync(url, BodyHandlers.ofString())

	fun <T> getAsync(url: String, handler: BodyHandler<T>): CompletableFuture<HttpResponse<T>> = HttpRequest
		.newBuilder()
		.uri(URI.create(url))
		.GET()
		.build()
		.let { client.sendAsync(it, handler) }
}
