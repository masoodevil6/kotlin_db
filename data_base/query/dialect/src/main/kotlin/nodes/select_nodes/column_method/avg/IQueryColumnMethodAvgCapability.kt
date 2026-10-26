package gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.avg

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.avg.IQueryColumnMethodAvgAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.avg.QueryColumnMethodAvgData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnMethodAvgCapability : IAstRenderer<IQueryColumnMethodAvgAst, QueryColumnMethodAvgData>
