package gog.my_project.data_base.query.builder.interfaces.builder.option_group

import gog.my_project.data_base.query.ast.interfaces.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery
import gog.my_project.data_base.query.builder.interfaces.builder.column_base.IQueryColumnsBaseBuilder

interface IQueryOptionGroupBuilder : IQuery {

    var ast: IQueryOptionGroupAst;

    fun addColumn(blockColumn: IQueryColumnsBaseBuilder.() -> Unit): IQueryOptionGroupBuilder

}