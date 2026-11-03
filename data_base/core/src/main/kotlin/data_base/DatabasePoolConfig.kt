package gog.my_project.data_base.core.data_base

/** Settings for a database connection pool. No pool is created by this configuration alone. */
data class DatabasePoolConfig(
    val maxPoolSize: Int = 10,
)
