package kr.ac.kaist.ecl.mjo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(
	val name: String,
	@SerialName("save_path") val savePath: String,
	@SerialName("last_modified") val lastModified: MutableMap<String, Long>
) : Comparable<AppConfig> {
	override fun compareTo(other: AppConfig): Int {
		return name.toLowerCase().compareTo(other.name.toLowerCase())
	}
}
