package gog.my_project.data_base.migration.dialect.nodes.create_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.create_foreign_key.IMigrationCreateForeignKeyAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationCreateForeignKeyCapability :
    IAstRenderer<IMigrationCreateForeignKeyAst, MigrationDataClass?>
