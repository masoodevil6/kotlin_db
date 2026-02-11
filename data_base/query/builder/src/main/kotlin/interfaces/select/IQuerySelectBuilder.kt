package gog.my_project.data_base.query.builder.interfaces.select

import gog.my_project.data_base.query.ast.interfaces.select.IQuerySelectAst
import gog.my_project.data_base.query.builder.interfaces.IQuery
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryColumnsBuilder

interface IQuerySelectBuilder : IQuery {

    var ast: IQuerySelectAst;

    fun addColumn(blockColumn: IQueryColumnsBuilder.() -> Unit): IQuerySelectBuilder;

}