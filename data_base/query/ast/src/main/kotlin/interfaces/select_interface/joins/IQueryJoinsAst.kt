package gog.my_project.data_base.query.ast.interfaces.select_interface.joins
import gog.my_project.data_base.query.ast.metadata.select.joins.QueryJoinsAstMetadata

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst


interface IQueryJoinsAst : IQueryAst<QueryJoinsAstMetadata> {

    var joins: MutableList<IQueryJoinsItemAst>

}