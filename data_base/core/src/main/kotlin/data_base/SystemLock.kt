package gog.my_project.data_base_operation.config.data_base

/** A descriptive logical identifier for a system-level lock; it does not execute or acquire it. */
data class SystemLock(
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "System lock identifier must not be blank" }
    }

    companion object {
        /** Lock used to serialize system migration orchestration for one database identity. */
        @JvmField
        val MIGRATION = SystemLock("migration")

        /** Lock reserved for coordinated system schema updates. */
        @JvmField
        val SCHEMA_UPDATE = SystemLock("schema-update")

        /** Lock reserved for system database initialization. */
        @JvmField
        val DATABASE_INITIALIZATION = SystemLock("database-initialization")
    }
}
