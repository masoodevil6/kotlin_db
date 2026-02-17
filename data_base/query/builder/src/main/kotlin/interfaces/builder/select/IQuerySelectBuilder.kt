package gog.my_project.data_base.query.builder.interfaces.builder.select

import gog.my_project.data_base.query.ast.interfaces.select.IQuerySelectAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery
import gog.my_project.data_base.query.builder.interfaces.builder.columns.IQueryColumnsBuilder

interface IQuerySelectBuilder : IQuery {

    var ast: IQuerySelectAst;

    fun addColumn(blockColumn: IQueryColumnsBuilder.() -> Unit): IQuerySelectBuilder;

}