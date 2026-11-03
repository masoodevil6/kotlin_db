package gog.my_project.data_base_schema.builder.ast.create_table.definition

import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTablePrimaryKeyApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableUniqueApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableFullTextIndexApi
import gog.my_project.data_base_schema.api.interfaces.create_table.definition.ISchemaCreateTableForeignKeyApi
import gog.my_project.data_base_schema.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.ForeignKeyDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.FullTextIndexDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.IndexDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.PrimaryKeyDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.definition.UniqueDefinitionAst
import gog.my_project.data_base_schema.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.ForeignKeyDefinitionAstMetadata
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.FullTextIndexDefinitionAstMetadata
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.IndexDefinitionAstMetadata
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.PrimaryKeyDefinitionAstMetadata
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.UniqueDefinitionAstMetadata

class SchemaCreateTablePrimaryKeyBuilder : ISchemaCreateTablePrimaryKeyApi {
    private var columnNames: List<String>? = null

    override fun columns(vararg columns: String): ISchemaCreateTablePrimaryKeyApi = apply {
        columnNames = columns.map(String::trim)
    }

    fun build(): PrimaryKeyDefinitionAst = PrimaryKeyDefinitionAst(
        PrimaryKeyDefinitionAstMetadata(columnNames.orEmpty()),
    )
}

class SchemaCreateTableUniqueBuilder : ISchemaCreateTableUniqueApi {
    private var indexName: String? = null
    private var columnNames: List<String>? = null

    override fun name(name: String): ISchemaCreateTableUniqueApi = apply {
        indexName = name.trim()
    }

    override fun columns(vararg columns: String): ISchemaCreateTableUniqueApi = apply {
        columnNames = columns.map(String::trim)
    }

    fun build(): UniqueDefinitionAst = UniqueDefinitionAst(
        UniqueDefinitionAstMetadata(
            indexName.orEmpty(),
            columnNames.orEmpty(),
        ),
    )
}

class SchemaCreateTableIndexBuilder : ISchemaCreateTableIndexApi {
    private var indexName: String? = null
    private var columnNames: List<String>? = null
    private var indexMethod: IndexMethod? = null

    override fun name(name: String): ISchemaCreateTableIndexApi = apply {
        indexName = name.trim()
    }

    override fun columns(vararg columns: String): ISchemaCreateTableIndexApi = apply {
        columnNames = columns.map(String::trim)
    }

    override fun using(method: IndexMethod): ISchemaCreateTableIndexApi = apply {
        indexMethod = method
    }

    fun build(): IndexDefinitionAst = IndexDefinitionAst(
        IndexDefinitionAstMetadata(
            indexName.orEmpty(),
            columnNames.orEmpty(),
            indexMethod,
        ),
    )
}

class SchemaCreateTableFullTextIndexBuilder : ISchemaCreateTableFullTextIndexApi {
    private var indexName: String? = null
    private var columnNames: List<String>? = null

    override fun name(name: String): ISchemaCreateTableFullTextIndexApi = apply {
        indexName = name.trim()
    }

    override fun columns(vararg columns: String): ISchemaCreateTableFullTextIndexApi = apply {
        columnNames = columns.map(String::trim)
    }

    fun build(): FullTextIndexDefinitionAst = FullTextIndexDefinitionAst(
        FullTextIndexDefinitionAstMetadata(
            indexName.orEmpty(),
            columnNames.orEmpty(),
        ),
    )
}

class SchemaCreateTableForeignKeyBuilder : ISchemaCreateTableForeignKeyApi {
    private var constraintName: String? = null
    private var columnNames: List<String>? = null
    private var referencesTableName: String? = null
    private var referencesColumnNames: List<String>? = null
    private var onDelete: ForeignKeyAction? = null
    private var onUpdate: ForeignKeyAction? = null

    override fun name(name: String): ISchemaCreateTableForeignKeyApi = apply {
        constraintName = name.trim()
    }

    override fun columns(vararg columns: String): ISchemaCreateTableForeignKeyApi = apply {
        columnNames = columns.map(String::trim)
    }

    override fun referencesTable(table: String): ISchemaCreateTableForeignKeyApi = apply {
        referencesTableName = table.trim()
    }

    override fun referencesColumns(vararg columns: String): ISchemaCreateTableForeignKeyApi = apply {
        referencesColumnNames = columns.map(String::trim)
    }

    override fun onDelete(action: ForeignKeyAction): ISchemaCreateTableForeignKeyApi = apply {
        onDelete = action
    }

    override fun onUpdate(action: ForeignKeyAction): ISchemaCreateTableForeignKeyApi = apply {
        onUpdate = action
    }

    fun build(): ForeignKeyDefinitionAst {
        return ForeignKeyDefinitionAst(
            ForeignKeyDefinitionAstMetadata(
                constraintName.orEmpty(),
                columnNames.orEmpty(),
                referencesTableName.orEmpty(),
                referencesColumnNames.orEmpty(),
                onDelete,
                onUpdate,
            ),
        )
    }
}
