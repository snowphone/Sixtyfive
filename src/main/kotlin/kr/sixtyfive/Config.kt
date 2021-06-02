package kr.sixtyfive

data class Config(val applications: MutableList<AppConfig>) {
	override fun toString() = applications
		.fold(Table().addLine()) { table, it ->
			table.addRow(it.name, it.save_path)
		}.addLine().render()

	/**
	 * Gets an app configuration whose name is `processName`
	 */
	operator fun get(processName: String) = applications.firstOrNull { it.name == processName }

	/**
	 * Updates current host's last modified time
	 */
	operator fun set(processName: String, lastModifiedTime: Long) {
		this[processName]?.last_modified?.put(hostName, lastModifiedTime)
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
