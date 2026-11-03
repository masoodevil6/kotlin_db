package gog.my_project.data_base_query.dialect.nodes.select_nodes.joins

import gog.my_project.data_base_query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base_query.dialect.data_class.select_data.joins.QueryJoinsData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryJoinsCapability : IAstRenderer<IQueryJoinsAst, QueryJoinsData> {
}