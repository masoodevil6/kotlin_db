package gog.my_project.data_base.migration.builder.ast.create_table.definition

import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTablePrimaryKeyApi
import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableUniqueApi
import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableFullTextIndexApi
import gog.my_project.data_base.migration.api.interfaces.create_table.definition.IMigrationCreateTableForeignKeyApi
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.ForeignKeyDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.FullTextIndexDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.IndexDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.PrimaryKeyDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.UniqueDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction

class MigrationCreateTablePrimaryKeyBuilder : IMigrationCreateTablePrimaryKeyApi {
    private var columnNames: List<String>? = null

    override fun columns(vararg columns: String): IMigrationCreateTablePrimaryKeyApi = apply {
        columnNames = columns.map(String::trim)
    }

    fun build(): PrimaryKeyDefinitionAst = PrimaryKeyDefinitionAst(
        validateColumns(columnNames, "PRIMARY KEY"),
    )
}

class MigrationCreateTableUniqueBuilder : IMigrationCreateTableUniqueApi {
    private var indexName: String? = null
    private var columnNames: List<String>? = null

    override fun name(name: String): IMigrationCreateTableUniqueApi = apply {
        indexName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateTableUniqueApi = apply {
        columnNames = columns.map(String::trim)
    }

    fun build(): UniqueDefinitionAst = UniqueDefinitionAst(
        requireIdentifier(indexName, "UNIQUE"),
        validateColumns(columnNames, "UNIQUE"),
    )
}

class MigrationCreateTableIndexBuilder : IMigrationCreateTableIndexApi {
    private var indexName: String? = null
    private var columnNames: List<String>? = null
    private var indexMethod: IndexMethod? = null

    override fun name(name: String): IMigrationCreateTableIndexApi = apply {
        indexName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateTableIndexApi = apply {
        columnNames = columns.map(String::trim)
    }

    override fun using(method: IndexMethod): IMigrationCreateTableIndexApi = apply {
        indexMethod = method
    }

    fun build(): IndexDefinitionAst = IndexDefinitionAst(
        requireIdentifier(indexName, "INDEX"),
        validateColumns(columnNames, "INDEX"),
        indexMethod,
    )
}

class MigrationCreateTableFullTextIndexBuilder : IMigrationCreateTableFullTextIndexApi {
    private var indexName: String? = null
    private var columnNames: List<String>? = null

    override fun name(name: String): IMigrationCreateTableFullTextIndexApi = apply {
        indexName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateTableFullTextIndexApi = apply {
        columnNames = columns.map(String::trim)
    }

    fun build(): FullTextIndexDefinitionAst = FullTextIndexDefinitionAst(
        requireIdentifier(indexName, "FULLTEXT INDEX"),
        validateColumns(columnNames, "FULLTEXT INDEX"),
    )
}

class MigrationCreateTableForeignKeyBuilder : IMigrationCreateTableForeignKeyApi {
    private var constraintName: String? = null
    private var columnNames: List<String>? = null
    private var referencesTableName: String? = null
    private var referencesColumnNames: List<String>? = null
    private var onDelete: ForeignKeyAction? = null
    private var onUpdate: ForeignKeyAction? = null

    override fun name(name: String): IMigrationCreateTableForeignKeyApi = apply {
        constraintName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateTableForeignKeyApi = apply {
        columnNames = columns.map(String::trim)
    }

    override fun referencesTable(table: String): IMigrationCreateTableForeignKeyApi = apply {
        referencesTableName = table.trim()
    }

    override fun referencesColumns(vararg columns: String): IMigrationCreateTableForeignKeyApi = apply {
        referencesColumnNames = columns.map(String::trim)
    }

    override fun onDelete(action: ForeignKeyAction): IMigrationCreateTableForeignKeyApi = apply {
        onDelete = action
    }

    override fun onUpdate(action: ForeignKeyAction): IMigrationCreateTableForeignKeyApi = apply {
        onUpdate = action
    }

    fun build(): ForeignKeyDefinitionAst {
        val localColumns = validateColumns(columnNames, "FOREIGN KEY local")
        val referenceColumns = validateColumns(referencesColumnNames, "FOREIGN KEY referenced")
        require(localColumns.size == referenceColumns.size) {
            "FOREIGN KEY local and referenced column lists must have equal size"
        }
        return ForeignKeyDefinitionAst(
            requireIdentifier(constraintName, "FOREIGN KEY"),
            localColumns,
            requireIdentifier(referencesTableName, "FOREIGN KEY referenced table"),
            referenceColumns,
            onDelete,
            onUpdate,
        )
    }
}

private fun requireIdentifier(value: String?, label: String): String =
    requireNotNull(value?.takeIf(String::isNotBlank)) { "$label requires a non-blank name" }

private fun validateColumns(values: List<String>?, label: String): List<String> {
    val columns = requireNotNull(values) { "$label requires at least one column" }
    require(columns.isNotEmpty()) { "$label requires at least one column" }
    require(columns.all(String::isNotBlank)) { "$label column names must not be blank" }
    for (currentIndex in columns.indices) {
        for (previousIndex in 0 until currentIndex) {
            require(!columns[currentIndex].equals(columns[previousIndex], ignoreCase = true)) {
                "$label column names must not contain duplicates"
            }
        }
    }
    return columns
}
