package gog.my_project.data_base.query.dialect.nodes.joins

import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst
import gog.my_project.data_base.query.dialect.data_class.joins.QueryJoinsData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryJoinsCapability : IAstRenderer<IQueryJoinsAst, QueryJoinsData> {
}