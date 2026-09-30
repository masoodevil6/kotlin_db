package gog.my_project.data_base.migration.dialect.nodes.drop_column

import gog.my_project.data_base.migration.ast.interfaces.drop_column.IMigrationDropColumnAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationDropColumnCapability :
    IAstRenderer<IMigrationDropColumnAst, MigrationDataClass?>
