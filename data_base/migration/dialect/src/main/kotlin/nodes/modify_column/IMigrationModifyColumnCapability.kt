package gog.my_project.data_base.migration.dialect.nodes.modify_column

import gog.my_project.data_base.migration.ast.interfaces.modify_column.IMigrationModifyColumnAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationModifyColumnCapability : IAstRenderer<IMigrationModifyColumnAst, MigrationDataClass?>
