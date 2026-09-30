package gog.my_project.data_base.migration.dialect.nodes.drop_table

import gog.my_project.data_base.migration.ast.interfaces.drop_table.IMigrationDropTableAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationDropTableCapability :
    IAstRenderer<IMigrationDropTableAst, MigrationDataClass?>