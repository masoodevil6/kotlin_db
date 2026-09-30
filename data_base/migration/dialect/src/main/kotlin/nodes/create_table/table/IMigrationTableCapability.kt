package gog.my_project.data_base.migration.dialect.nodes.create_table.table

import gog.my_project.data_base.migration.ast.interfaces.create_table.table.IMigrationTableAst
import gog.my_project.data_base.migration.dialect.data_class.create_table.table.MigrationTableData
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationTableCapability : IAstRenderer<IMigrationTableAst, MigrationTableData> {
}