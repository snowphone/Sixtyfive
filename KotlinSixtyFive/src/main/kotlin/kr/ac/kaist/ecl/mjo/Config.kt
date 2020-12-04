package kr.ac.kaist.ecl.mjo

import kotlinx.serialization.Serializable

@Serializable
data class Config(val applications: MutableList<AppConfig>)
