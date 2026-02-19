package gog.my_project.data_base.query.dialect.nodes.select_nodes.joins

import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.dialect.data_class.select_data.joins.QueryJoinsData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryJoinsCapability : IAstRenderer<IQueryJoinsAst, QueryJoinsData> {
}