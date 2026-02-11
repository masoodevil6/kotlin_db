package gog.my_project.data_base.query.dialect.nodes.withs

import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst
import gog.my_project.data_base.query.dialect.data_class.withs.QueryWithsData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryWithsCapability  : IAstRenderer<IQueryWithsAst , QueryWithsData> {



}