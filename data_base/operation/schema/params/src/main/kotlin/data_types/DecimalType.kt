package gog.my_project.data_base_schema.params.data_types

data class DecimalType(val precision: Int, val scale: Int) : SchemaColumnDataType() {
    init {
        require(precision > 0) { "DECIMAL precision must be positive" }
        require(scale >= 0 && scale <= precision) { "DECIMAL scale must be between 0 and precision" }
    }
}
