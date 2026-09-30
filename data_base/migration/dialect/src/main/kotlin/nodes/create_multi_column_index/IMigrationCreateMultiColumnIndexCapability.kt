package gog.my_project.data_base.migration.dialect.nodes.create_multi_column_index

import gog.my_project.data_base.migration.ast.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationCreateMultiColumnIndexCapability :
    IAstRenderer<IMigrationCreateMultiColumnIndexAst, MigrationDataClass?>
