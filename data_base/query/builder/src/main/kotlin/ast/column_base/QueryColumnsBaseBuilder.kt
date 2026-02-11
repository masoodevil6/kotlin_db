package gog.my_project.data_base.query.builder.ast.column_base

import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.annotations.ctes.QBCteSelect
import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.managers.models.IModel
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.schema.column_base.QueryColumnsBaseAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

class QueryColumnsBaseBuilder(
    override var ast: IQueryColumnsBaseAst = QueryColumnsBaseAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) : IQueryColumnsBaseApi {


    override fun <T : IModel, R> column(
        table: KClass<T>,
        column: KProperty1<T, R>
    ): IQueryColumnsBaseApi {
        this.ast.table =         table.findAnnotation<QBTable>()?.name;
        this.ast.tableAlias =    table.findAnnotation<QBTable>()?.alias;
        this.ast.column =        column.findAnnotation<QBColumn>()?.name;
        return this;
    }


    override fun <T : ICte, R> cteColumn(
        cte: KClass<T>,
        select: KProperty1<T, R>
    ): IQueryColumnsBaseApi {
        this.ast.cte =         cte.findAnnotation<QBCte>()?.name;
        this.ast.cteAlias =    cte.findAnnotation<QBCte>()?.alias;
        this.ast.select =      select.findAnnotation<QBCteSelect>()?.name;
        return this;
    }

}