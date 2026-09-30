package gog.my_project.data_base.migration.params.data_types

data class TimestampType(val precision: Int? = null) : MigrationColumnDataType() {
    init {
        require(precision == null || precision in 0..6) {
            "TIMESTAMP fractional-second precision must be between 0 and 6"
        }
    }
}
