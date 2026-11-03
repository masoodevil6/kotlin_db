package gog.my_project.data_base_schema

@DslMarker
annotation class SchemaDsl

@SchemaDsl
interface SchemaTableDefinition {
    fun tableName(table: String)
}

@SchemaDsl
interface SchemaColumnDefinition {
    fun name(name: String)
    fun dataType(dataType: SchemaDataType)
    fun default(value: Any?)
    fun isNullable(nullable: Boolean)
    fun isAutoIncrement(enabled: Boolean)
    fun isPrimary(enabled: Boolean)

    fun nullable() = isNullable(true)
    fun notNull() = isNullable(false)
    fun autoIncrement() = isAutoIncrement(true)
    fun primaryKey() = isPrimary(true)
}

@SchemaDsl
interface SchemaAddColumnDefinition : SchemaColumnDefinition {
    fun tableName(table: String)
}

@SchemaDsl
interface SchemaCreateTablePrimaryKeyDefinition {
    fun columns(vararg columns: String)
}

@SchemaDsl
interface SchemaCreateTableUniqueDefinition {
    fun name(name: String)
    fun columns(vararg columns: String)
}

@SchemaDsl
interface SchemaCreateTableIndexDefinition {
    fun name(name: String)
    fun columns(vararg columns: String)
    fun using(method: SchemaIndexMethod)
}

@SchemaDsl
interface SchemaCreateTableFullTextIndexDefinition {
    fun name(name: String)
    fun columns(vararg columns: String)
}

@SchemaDsl
interface SchemaForeignKeyDefinition {
    fun name(name: String)
    fun columns(vararg columns: String)
    fun referencesTable(table: String)
    fun referencesColumns(vararg columns: String)
    fun onDelete(action: SchemaForeignKeyAction)
    fun onUpdate(action: SchemaForeignKeyAction)
}

@SchemaDsl
interface SchemaCreateTableDefinition {
    fun table(block: SchemaTableDefinition.() -> Unit)
    fun addColumn(block: SchemaColumnDefinition.() -> Unit)
    fun timestamps()
    fun softDeletes()
    fun primaryKey(block: SchemaCreateTablePrimaryKeyDefinition.() -> Unit)
    fun unique(block: SchemaCreateTableUniqueDefinition.() -> Unit)
    fun index(block: SchemaCreateTableIndexDefinition.() -> Unit)
    fun fullTextIndex(block: SchemaCreateTableFullTextIndexDefinition.() -> Unit)
    fun foreignKey(block: SchemaForeignKeyDefinition.() -> Unit)
    fun ifNotExists()
}

@SchemaDsl
interface SchemaDropTableDefinition {
    fun tableName(table: String)
    fun ifExists()
}

@SchemaDsl
interface SchemaRenameTableDefinition {
    fun fromTableName(table: String)
    fun toTableName(table: String)
}

@SchemaDsl
interface SchemaDropColumnDefinition {
    fun tableName(table: String)
    fun name(name: String)
    fun ifExists()
}

@SchemaDsl
interface SchemaRenameColumnDefinition {
    fun tableName(table: String)
    fun name(name: String)
    fun to(name: String)
}

@SchemaDsl
interface SchemaModifyColumnDefinition {
    fun tableName(table: String)
    fun name(name: String)
    fun dataType(dataType: SchemaDataType)
    fun default(value: Any?)
    fun isNullable(nullable: Boolean)
    fun autoIncrement(enabled: Boolean)

    fun nullable() = isNullable(true)
    fun notNull() = isNullable(false)
}

@SchemaDsl
interface SchemaCreateIndexDefinition {
    fun tableName(table: String)
    fun name(name: String)
    fun column(column: String)
    fun using(method: SchemaIndexMethod)
}

@SchemaDsl
interface SchemaDropIndexDefinition {
    fun tableName(table: String)
    fun name(name: String)
}

@SchemaDsl
interface SchemaCreateUniqueIndexDefinition {
    fun tableName(table: String)
    fun name(name: String)
    fun column(column: String)
}

@SchemaDsl
interface SchemaCreateMultiColumnIndexDefinition {
    fun tableName(table: String)
    fun name(name: String)
    fun columns(vararg columns: String)
}

@SchemaDsl
interface SchemaCreateFullTextIndexDefinition {
    fun tableName(table: String)
    fun name(name: String)
    fun columns(vararg columns: String)
}

@SchemaDsl
interface SchemaCreateSpatialIndexDefinition {
    fun tableName(table: String)
    fun name(name: String)
    fun column(column: String)
}

@SchemaDsl
interface SchemaCreateForeignKeyDefinition : SchemaForeignKeyDefinition {
    fun tableName(table: String)
}

@SchemaDsl
interface SchemaDropForeignKeyDefinition {
    fun tableName(table: String)
    fun name(name: String)
}
