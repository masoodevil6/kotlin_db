package gog.my_project.data_base.migration.params.data_types

data class TimeType(val precision: Int? = null) : MigrationColumnDataType() {
    init {
        require(precision == null || precision in 0..6) {
            "TIME fractional-second precision must be between 0 and 6"
        }
    }
}
