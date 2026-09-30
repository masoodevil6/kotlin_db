package gog.my_project.data_base.migration.example.managers

import gog.my_project.data_base.migration.dialect.interfaces.ISqlDialect
import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor
import gog.my_project.data_base.migration.example.v1.migrations.drop_table.A1ExampleDropTableV1
import gog.my_project.data_base.migration.example.v1.migrations.rename_table.A1ExampleRenameTableV1
import gog.my_project.data_base.migration.example.v1.migrations.add_column.A1ExampleAddColumnV1
import gog.my_project.data_base.migration.example.v1.migrations.drop_column.A1ExampleDropColumnV1
import gog.my_project.data_base.migration.example.v1.migrations.rename_column.A1ExampleRenameColumnV1
import gog.my_project.data_base.migration.example.v1.migrations.modify_column.A1ExampleModifyColumnV1
import gog.my_project.data_base.migration.example.v1.migrations.create_index.A1ExampleCreateIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.drop_index.A1ExampleDropIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_unique_index.A1ExampleCreateUniqueIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_multi_column_index.A1ExampleCreateMultiColumnIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_full_text_index.A1ExampleCreateFullTextIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_spatial_index.A1ExampleCreateSpatialIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_foreign_key.A1ExampleCreateForeignKeyV1
import gog.my_project.data_base.migration.example.v1.migrations.drop_foreign_key.A1ExampleDropForeignKeyV1

interface IManagerExample {
    val statusRunCreateTable:      Boolean
    val statusExecute:             Boolean
    val statusRunDropTable:        Boolean
    val statusRunRenameTable:      Boolean
    val statusRunAddColumn:        Boolean
    val statusRunDropColumn:       Boolean
    val statusRunRenameColumn:     Boolean
    val statusRunModifyColumn:     Boolean
    val statusRunCreateIndex:      Boolean
    val statusRunDropIndex:        Boolean
    val statusRunCreateUniqueIndex: Boolean
    val statusRunCreateMultiColumnIndex: Boolean
    val statusRunCreateFullTextIndex: Boolean
    val statusRunCreateSpatialIndex: Boolean
    val statusRunCreateForeignKey: Boolean
    val statusRunDropForeignKey: Boolean
    var listExamplesCreateTable:   ArrayList<IExampleV1>
    var listExamplesDropTable:     ArrayList<A1ExampleDropTableV1>
    var listExamplesRenameTable:   ArrayList<A1ExampleRenameTableV1>
    var listExamplesAddColumn:     ArrayList<A1ExampleAddColumnV1>
    var listExamplesDropColumn:    ArrayList<A1ExampleDropColumnV1>
    var listExamplesRenameColumn:  ArrayList<A1ExampleRenameColumnV1>
    var listExamplesModifyColumn:  ArrayList<A1ExampleModifyColumnV1>
    var listExamplesCreateIndex:   ArrayList<A1ExampleCreateIndexV1>
    var listExamplesDropIndex:     ArrayList<A1ExampleDropIndexV1>
    var listExamplesCreateUniqueIndex: ArrayList<A1ExampleCreateUniqueIndexV1>
    var listExamplesCreateMultiColumnIndex: ArrayList<A1ExampleCreateMultiColumnIndexV1>
    var listExamplesCreateFullTextIndex: ArrayList<A1ExampleCreateFullTextIndexV1>
    var listExamplesCreateSpatialIndex: ArrayList<A1ExampleCreateSpatialIndexV1>
    var listExamplesCreateForeignKey: ArrayList<A1ExampleCreateForeignKeyV1>
    var listExamplesDropForeignKey: ArrayList<A1ExampleDropForeignKeyV1>

    fun readyListExamples()

    fun renderExamples(
        dialect: ISqlDialect,
        migrationExecutor: IMigrationExecutor,
    ) {
        if (statusRunCreateTable) {
            listExamplesCreateTable.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunDropTable) {
            listExamplesDropTable.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunAddColumn) {
            listExamplesAddColumn.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunRenameTable) {
            listExamplesRenameTable.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunDropColumn) {
            listExamplesDropColumn.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunRenameColumn) {
            listExamplesRenameColumn.forEach { example ->
                if (statusExecute) {
                    // Legacy CHANGE COLUMN SQL needs the live source definition, resolved by execution.
                    example.execute(migrationExecutor)
                } else {
                    example.render(dialect)
                }
            }
        }

        if (statusRunModifyColumn) {
            listExamplesModifyColumn.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunCreateIndex) {
            listExamplesCreateIndex.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunDropIndex) {
            listExamplesDropIndex.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunCreateUniqueIndex) {
            listExamplesCreateUniqueIndex.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunCreateMultiColumnIndex) {
            listExamplesCreateMultiColumnIndex.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunCreateFullTextIndex) {
            listExamplesCreateFullTextIndex.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunCreateSpatialIndex) {
            listExamplesCreateSpatialIndex.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunCreateForeignKey) {
            listExamplesCreateForeignKey.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }

        if (statusRunDropForeignKey) {
            listExamplesDropForeignKey.forEach { example ->
                example.render(dialect)
                if (statusExecute) example.execute(migrationExecutor)
            }
        }
    }
}
