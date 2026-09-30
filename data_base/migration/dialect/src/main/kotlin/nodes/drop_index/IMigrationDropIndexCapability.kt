package gog.my_project.data_base.migration.dialect.nodes.drop_index

import gog.my_project.data_base.migration.ast.interfaces.drop_index.IMigrationDropIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationDropIndexCapability : IAstRenderer<IMigrationDropIndexAst, MigrationDataClass?>
