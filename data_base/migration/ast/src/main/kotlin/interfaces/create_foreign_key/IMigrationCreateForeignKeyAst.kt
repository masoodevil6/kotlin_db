package gog.my_project.data_base.migration.ast.interfaces.create_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction

interface IMigrationCreateForeignKeyAst : IMigrationAst {
    var tableName: String?
    var constraintName: String?
    var columnNames: List<String>?
    var referencesTableName: String?
    var referencesColumnNames: List<String>?
    var onDelete: ForeignKeyAction?
    var onUpdate: ForeignKeyAction?
}
