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
}
