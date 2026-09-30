package gog.my_project.data_base.migration.dialect.nodes.create_unique_index

import gog.my_project.data_base.migration.ast.interfaces.create_unique_index.IMigrationCreateUniqueIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationCreateUniqueIndexCapability :
    IAstRenderer<IMigrationCreateUniqueIndexAst, MigrationDataClass?>
