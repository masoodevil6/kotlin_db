package gog.my_project.data_base.query.dialect.nodes.select_nodes.withs

import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst
import gog.my_project.data_base.query.dialect.data_class.select_data.withs.QueryWithsData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryWithsCapability  : IAstRenderer<IQueryWithsAst, QueryWithsData> {



}