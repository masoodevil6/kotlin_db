package gog.my_project.data_base.migration.api.interfaces

interface Migration {
    fun up(): MigrationDefinition

    fun down(): MigrationDefinition
}
