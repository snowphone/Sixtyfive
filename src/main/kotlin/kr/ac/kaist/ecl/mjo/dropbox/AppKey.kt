package kr.ac.kaist.ecl.mjo.dropbox

import kotlinx.serialization.Serializable

@Serializable
data class AppKey(
	val key: String,
	val secret: String,
)
