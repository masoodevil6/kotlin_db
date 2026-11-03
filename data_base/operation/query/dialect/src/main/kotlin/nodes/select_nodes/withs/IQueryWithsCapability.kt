package gog.my_project.data_base_query.dialect.nodes.select_nodes.withs

import gog.my_project.data_base_query.ast.interfaces.select_interface.withs.IQueryWithsAst
import gog.my_project.data_base_query.dialect.data_class.select_data.withs.QueryWithsData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryWithsCapability  : IAstRenderer<IQueryWithsAst, QueryWithsData> {



}