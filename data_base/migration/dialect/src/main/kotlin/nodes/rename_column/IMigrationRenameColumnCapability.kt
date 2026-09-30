package gog.my_project.data_base.migration.dialect.nodes.rename_column

import gog.my_project.data_base.migration.ast.interfaces.rename_column.IMigrationRenameColumnAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationRenameColumnCapability : IAstRenderer<IMigrationRenameColumnAst, MigrationDataClass?>
