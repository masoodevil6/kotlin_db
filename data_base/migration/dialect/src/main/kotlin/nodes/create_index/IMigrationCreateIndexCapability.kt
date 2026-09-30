package gog.my_project.data_base.migration.dialect.nodes.create_index

import gog.my_project.data_base.migration.ast.interfaces.create_index.IMigrationCreateIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationCreateIndexCapability : IAstRenderer<IMigrationCreateIndexAst, MigrationDataClass?>
