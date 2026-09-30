package gog.my_project.data_base.migration.dialect.nodes.rename_table

import gog.my_project.data_base.migration.ast.interfaces.rename_table.IMigrationRenameTableAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationRenameTableCapability :
    IAstRenderer<IMigrationRenameTableAst, MigrationDataClass?>