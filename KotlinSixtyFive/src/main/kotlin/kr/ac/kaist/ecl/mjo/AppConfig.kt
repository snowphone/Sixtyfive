package kr.ac.kaist.ecl.mjo

import kotlinx.serialization.Serializable

@Serializable
data class AppConfig(val name: String, val savePath: String) : Comparable<AppConfig> {
	override fun compareTo(other: AppConfig): Int {
		return name.toLowerCase().compareTo(other.name.toLowerCase())
	}
}
