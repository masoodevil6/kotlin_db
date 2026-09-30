package gog.my_project.data_base.migration.example.managers

import gog.my_project.data_base.migration.example.v1.migrations.IExampleV1
import gog.my_project.data_base.migration.example.v1.migrations.create_table.A1ExampleCreateTableV1
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
import gog.my_project.data_base.migration.example.v1.migrations.create_table_constraints.A1ExampleCreateTableConstraintsV1
import gog.my_project.data_base.migration.example.v1.migrations.create_table_timestamps.A1ExampleCreateTableTimestampsV1
import gog.my_project.data_base.migration.example.v1.migrations.create_spatial_index.A1ExampleCreateSpatialIndexV1
import gog.my_project.data_base.migration.example.v1.migrations.create_foreign_key.A1ExampleCreateForeignKeyV1
import gog.my_project.data_base.migration.example.v1.migrations.drop_foreign_key.A1ExampleDropForeignKeyV1

class ManagerExampleV1(
    override val statusRunCreateTable:            Boolean = false,
    override val statusExecute:                   Boolean = false,
    override val statusRunDropTable:              Boolean = false,
    override val statusRunRenameTable:            Boolean = false,
    override val statusRunAddColumn:              Boolean = false,
    override val statusRunDropColumn:             Boolean = false,
    override val statusRunRenameColumn:           Boolean = false,
    override val statusRunModifyColumn:           Boolean = false,
    override val statusRunCreateIndex:            Boolean = false,
    override val statusRunDropIndex:              Boolean = false,
    override val statusRunCreateUniqueIndex:      Boolean = false,
    override val statusRunCreateMultiColumnIndex: Boolean = false,
    override val statusRunCreateFullTextIndex:    Boolean = false,
    override val statusRunCreateSpatialIndex:     Boolean = false,
    override val statusRunCreateForeignKey:       Boolean = false,
    override val statusRunDropForeignKey:         Boolean = false,
) : IManagerExample {

    override var listExamplesCreateTable: ArrayList<IExampleV1> = arrayListOf()
    override var listExamplesDropTable: ArrayList<A1ExampleDropTableV1> = arrayListOf()
    override var listExamplesRenameTable: ArrayList<A1ExampleRenameTableV1> = arrayListOf()
    override var listExamplesAddColumn: ArrayList<A1ExampleAddColumnV1> = arrayListOf()
    override var listExamplesDropColumn: ArrayList<A1ExampleDropColumnV1> = arrayListOf()
    override var listExamplesRenameColumn: ArrayList<A1ExampleRenameColumnV1> = arrayListOf()
    override var listExamplesModifyColumn: ArrayList<A1ExampleModifyColumnV1> = arrayListOf()
    override var listExamplesCreateIndex: ArrayList<A1ExampleCreateIndexV1> = arrayListOf()
    override var listExamplesDropIndex: ArrayList<A1ExampleDropIndexV1> = arrayListOf()
    override var listExamplesCreateUniqueIndex: ArrayList<A1ExampleCreateUniqueIndexV1> = arrayListOf()
    override var listExamplesCreateMultiColumnIndex: ArrayList<A1ExampleCreateMultiColumnIndexV1> = arrayListOf()
    override var listExamplesCreateFullTextIndex: ArrayList<A1ExampleCreateFullTextIndexV1> = arrayListOf()
    override var listExamplesCreateSpatialIndex: ArrayList<A1ExampleCreateSpatialIndexV1> = arrayListOf()
    override var listExamplesCreateForeignKey: ArrayList<A1ExampleCreateForeignKeyV1> = arrayListOf()
    override var listExamplesDropForeignKey: ArrayList<A1ExampleDropForeignKeyV1> = arrayListOf()

    override fun readyListExamples() {

        listExamplesCreateTable.add(A1ExampleCreateTableV1())
        listExamplesCreateTable.add(A1ExampleCreateTableConstraintsV1())
        listExamplesCreateTable.add(A1ExampleCreateTableTimestampsV1())

        listExamplesDropTable.add(A1ExampleDropTableV1(useIfExists = false))
        listExamplesDropTable.add(A1ExampleDropTableV1(useIfExists = true))

        listExamplesRenameTable.add(A1ExampleRenameTableV1())

        listExamplesAddColumn.add(A1ExampleAddColumnV1())

        listExamplesDropColumn.add(A1ExampleDropColumnV1(useIfExists = false))
        listExamplesDropColumn.add(A1ExampleDropColumnV1(useIfExists = true))

        listExamplesRenameColumn.add(A1ExampleRenameColumnV1())

        listExamplesModifyColumn.add(A1ExampleModifyColumnV1())

        listExamplesCreateIndex.add(A1ExampleCreateIndexV1())

        listExamplesDropIndex.add(A1ExampleDropIndexV1())

        listExamplesCreateUniqueIndex.add(A1ExampleCreateUniqueIndexV1())

        listExamplesCreateMultiColumnIndex.add(A1ExampleCreateMultiColumnIndexV1())

        listExamplesCreateFullTextIndex.add(A1ExampleCreateFullTextIndexV1())

        listExamplesCreateSpatialIndex.add(A1ExampleCreateSpatialIndexV1())

        listExamplesCreateForeignKey.add(A1ExampleCreateForeignKeyV1())
        listExamplesDropForeignKey.add(A1ExampleDropForeignKeyV1())
    }
}
