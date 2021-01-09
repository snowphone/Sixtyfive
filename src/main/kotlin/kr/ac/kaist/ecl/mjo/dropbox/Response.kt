package kr.ac.kaist.ecl.mjo.dropbox

import kotlinx.serialization.Serializable

@Serializable
data class Response(
    val name: String,
    val path_lower: String,
    val path_display: String,
    val id: String,
    val client_modified: String,
    val server_modified: String,
    val rev: String,
    val size: Long,
    val is_downloadable: Boolean,
    val content_hash: String,
)

