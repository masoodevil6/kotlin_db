package gog.my_project.data_base.query.builder.interfaces.columns

import gog.my_project.data_base.query.api.tools.enums.SqlMethodColumn
import gog.my_project.data_base.query.ast.interfaces.column.IQueryColumnsAst
import gog.my_project.data_base.query.builder.interfaces.IQuery
import gog.my_project.data_base.query.builder.interfaces.column_base.IQueryColumnsBaseBuilder

interface IQueryColumnsBuilder : IQuery {
    var ast: IQueryColumnsAst;

    fun method(method: SqlMethodColumn) : IQueryColumnsBuilder;
    fun sum() : IQueryColumnsBuilder;
    fun count() : IQueryColumnsBuilder;
    fun avg() : IQueryColumnsBuilder;
    fun min() : IQueryColumnsBuilder;
    fun max() : IQueryColumnsBuilder;

    fun column( blockColumn: IQueryColumnsBaseBuilder.() -> Unit): IQueryColumnsBuilder;

    fun alias(alias: String): IQueryColumnsBuilder;


}