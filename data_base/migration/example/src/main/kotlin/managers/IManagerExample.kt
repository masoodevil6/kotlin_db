package gog.my_project.data_base.migration.example.managers

import gog.my_project.data_base_schema.SchemaDialect
import gog.my_project.data_base_schema.SchemaVersion
import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1
import gog.my_project.data_base.migration.example.v1.migrations.add_column.A1ExampleAddColumnV1
import gog.my_project.data_base.migration.example.v1.migrations.create_foreign_key.A1ExampleCreateForeignKeyV1
import gog.my_project.data_base.migration.example.v1.migrations.create_full_text_index.A1ExampleCreateFullTextIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_index.A1ExampleCreateIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_multi_column_index.A1ExampleCreateMultiColumnIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_spatial_index.A1ExampleCreateSpatialIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_unique_index.A1ExampleCreateUniqueIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.drop_column.A1ExampleDropColumnV1
import gog.my_project.data_base.migration.example.v1.migrations.drop_foreign_key.A1ExampleDropForeignKeyV1
import gog.my_project.data_base.migration.example.v1.migrations.drop_index.A1ExampleDropIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.drop_table.A1ExampleDropTableV1
import gog.my_project.data_base.migration.example.v1.migrations.modify_column.A1ExampleModifyColumnV1
import gog.my_project.data_base.migration.example.v1.migrations.rename_column.A1ExampleRenameColumnV1
import gog.my_project.data_base.migration.example.v1.migrations.rename_table.A1ExampleRenameTableV1

interface IManagerExample {
    val statusRunCreateTable: Boolean
    val statusRunDropTable: Boolean
    val statusRunRenameTable: Boolean
    val statusRunAddColumn: Boolean
    val statusRunDropColumn: Boolean
    val statusRunRenameColumn: Boolean
    val statusRunModifyColumn: Boolean
    val statusRunCreateIndex: Boolean
    val statusRunDropIndex: Boolean
    val statusRunCreateUniqueIndex: Boolean
    val statusRunCreateMultiColumnIndex: Boolean
    val statusRunCreateFullTextIndex: Boolean
    val statusRunCreateSpatialIndex: Boolean
    val statusRunCreateForeignKey: Boolean
    val statusRunDropForeignKey: Boolean

    var listExamplesCreateTable: ArrayList<IExampleV1>
    var listExamplesDropTable: ArrayList<A1ExampleDropTableV1>
    var listExamplesRenameTable: ArrayList<A1ExampleRenameTableV1>
    var listExamplesAddColumn: ArrayList<A1ExampleAddColumnV1>
    var listExamplesDropColumn: ArrayList<A1ExampleDropColumnV1>
    var listExamplesRenameColumn: ArrayList<A1ExampleRenameColumnV1>
    var listExamplesModifyColumn: ArrayList<A1ExampleModifyColumnV1>
    var listExamplesCreateIndex: ArrayList<A1ExampleCreateIndexV1>
    var listExamplesDropIndex: ArrayList<A1ExampleDropIndexV1>
    var listExamplesCreateUniqueIndex: ArrayList<A1ExampleCreateUniqueIndexV1>
    var listExamplesCreateMultiColumnIndex: ArrayList<A1ExampleCreateMultiColumnIndexV1>
    var listExamplesCreateFullTextIndex: ArrayList<A1ExampleCreateFullTextIndexV1>
    var listExamplesCreateSpatialIndex: ArrayList<A1ExampleCreateSpatialIndexV1>
    var listExamplesCreateForeignKey: ArrayList<A1ExampleCreateForeignKeyV1>
    var listExamplesDropForeignKey: ArrayList<A1ExampleDropForeignKeyV1>

    fun readyListExamples()

    fun renderExamples(dialect: SchemaDialect, version: SchemaVersion? = null) {
        if (statusRunCreateTable) listExamplesCreateTable.forEach { it.render(dialect, version) }
        if (statusRunDropTable) listExamplesDropTable.forEach { it.render(dialect, version) }
        if (statusRunRenameTable) listExamplesRenameTable.forEach { it.render(dialect, version) }
        if (statusRunAddColumn) listExamplesAddColumn.forEach { it.render(dialect, version) }
        if (statusRunDropColumn) listExamplesDropColumn.forEach { it.render(dialect, version) }
        if (statusRunRenameColumn) listExamplesRenameColumn.forEach { it.render(dialect, version) }
        if (statusRunModifyColumn) listExamplesModifyColumn.forEach { it.render(dialect, version) }
        if (statusRunCreateIndex) listExamplesCreateIndex.forEach { it.render(dialect, version) }
        if (statusRunDropIndex) listExamplesDropIndex.forEach { it.render(dialect, version) }
        if (statusRunCreateUniqueIndex) listExamplesCreateUniqueIndex.forEach { it.render(dialect, version) }
        if (statusRunCreateMultiColumnIndex) listExamplesCreateMultiColumnIndex.forEach { it.render(dialect, version) }
        if (statusRunCreateFullTextIndex) listExamplesCreateFullTextIndex.forEach { it.render(dialect, version) }
        if (statusRunCreateSpatialIndex) listExamplesCreateSpatialIndex.forEach { it.render(dialect, version) }
        if (statusRunCreateForeignKey) listExamplesCreateForeignKey.forEach { it.render(dialect, version) }
        if (statusRunDropForeignKey) listExamplesDropForeignKey.forEach { it.render(dialect, version) }
    }
}
