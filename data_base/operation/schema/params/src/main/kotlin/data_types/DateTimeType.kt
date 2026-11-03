package gog.my_project.data_base_schema.params.data_types

data class DateTimeType(val precision: Int? = null) : SchemaColumnDataType() {
    init {
        require(precision == null || precision in 0..6) {
            "DATETIME fractional-second precision must be between 0 and 6"
        }
    }
}
