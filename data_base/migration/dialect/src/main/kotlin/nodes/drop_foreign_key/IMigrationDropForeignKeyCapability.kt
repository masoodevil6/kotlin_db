package gog.my_project.data_base.migration.dialect.nodes.drop_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.drop_foreign_key.IMigrationDropForeignKeyAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationDropForeignKeyCapability :
    IAstRenderer<IMigrationDropForeignKeyAst, MigrationDataClass?>
