package kr.sixtyfive

data class AppConfig(
	val name: String,
	val save_path: String,
	val last_modified: MutableMap<String, Long>
) : Comparable<AppConfig> {
	override fun compareTo(other: AppConfig): Int {
		return name.lowercase().compareTo(other.name.lowercase())
	}
}
