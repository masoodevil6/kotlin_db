package gog.my_project.data_base.migration.params.data_types

data class VarcharType(val length: Int) : MigrationColumnDataType() {
    init { require(length > 0) { "VARCHAR length must be positive" } }
}
