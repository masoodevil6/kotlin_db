package gog.my_project.data_base.migration.dialect.nodes.create_table.column

import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.dialect.data_class.create_table.column.MigrationColumnData
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationColumnCapability : IAstRenderer<IMigrationColumnAst, MigrationColumnData> {
}