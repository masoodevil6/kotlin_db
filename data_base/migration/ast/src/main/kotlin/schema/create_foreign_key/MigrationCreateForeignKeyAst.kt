package gog.my_project.data_base.migration.ast.schema.create_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.create_foreign_key.IMigrationCreateForeignKeyAst
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction

class MigrationCreateForeignKeyAst : IMigrationCreateForeignKeyAst {
    override var tableName: String? = null
    override var constraintName: String? = null
    override var columnNames: List<String>? = null
    override var referencesTableName: String? = null
    override var referencesColumnNames: List<String>? = null
    override var onDelete: ForeignKeyAction? = null
    override var onUpdate: ForeignKeyAction? = null
}
