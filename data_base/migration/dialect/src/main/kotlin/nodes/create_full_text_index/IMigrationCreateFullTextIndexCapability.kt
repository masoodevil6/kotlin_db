package gog.my_project.data_base.migration.dialect.nodes.create_full_text_index

import gog.my_project.data_base.migration.ast.interfaces.create_full_text_index.IMigrationCreateFullTextIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationCreateFullTextIndexCapability :
    IAstRenderer<IMigrationCreateFullTextIndexAst, MigrationDataClass?>
