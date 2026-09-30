package gog.my_project.data_base.migration.dialect.nodes.add_column

import gog.my_project.data_base.migration.ast.interfaces.add_column.IMigrationAddColumnAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationAddColumnCapability :
    IAstRenderer<IMigrationAddColumnAst, MigrationDataClass?>