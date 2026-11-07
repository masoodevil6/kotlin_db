package gog.my_project.data_base_operation.config.data_base

/** Settings for a database connection pool. No pool is created by this configuration alone. */
data class DatabasePoolConfig(
    val maxPoolSize: Int = 10,
)
