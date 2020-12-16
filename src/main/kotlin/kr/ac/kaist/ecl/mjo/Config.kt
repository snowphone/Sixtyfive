package kr.ac.kaist.ecl.mjo

import kotlinx.serialization.Serializable

@Serializable
data class Config(val applications: MutableList<AppConfig>) {
	override fun toString(): String {
		val len = this
			.applications
			.map(AppConfig::name)
			.maxOf(String::length)

		return applications
			.fold(StringBuilder(), { acc, it ->
				acc.appendLine("${it.name.padEnd(len)}: ${it.savePath}")
			})
			.toString()
	}

	/**
	 * Gets an app configuration whose name is `processName`
	 */
	operator fun get(processName: String) = applications.firstOrNull { it.name == processName }

	/**
	 * Updates current host's last modified time
	 */
	operator fun set(processName: String, lastModifiedTime: Long) {
		this[processName]?.lastModified?.put(hostName, lastModifiedTime)
	}

	/**
	 * Inserts or updates new app configuration and sort in order
	 */
	operator fun set(processName: String, appConfig: AppConfig) {
		this[processName]?.let(this.applications::remove)
		this.applications.add(appConfig)
		this.applications.sort()
	}
}
