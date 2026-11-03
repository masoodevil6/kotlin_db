package gog.my_project.data_base_schema.params.data_types

data class VarcharType(val length: Int) : SchemaColumnDataType() {
    init { require(length > 0) { "VARCHAR length must be positive" } }
}
