package gog.my_project.data_base.query.builder.ast.column_base

import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.annotations.ctes.QBCteSelect
import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.schema.column_base.QueryColumnsBaseAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

class QueryColumnsBaseBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>() ,
    override var ast: IQueryColumnsBaseAst = QueryColumnsBaseAst(),
) : IQueryColumnsBaseApi {



    /* override fun <T : IModelBase, R> column(
        table: KClass<T>,
        column: KProperty1<T, R>
    ): IQueryColumnsBaseApi {
        this.ast.table =            table.findAnnotation<QBTable>()?.name;
        this.ast.tableAlias =       table.findAnnotation<QBTable>()?.alias;
        this.ast.column =           column.findAnnotation<QBColumn>()?.name;
        this.ast.columnAlias =      column.findAnnotation<QBColumn>()?.alias;
        return this;
    }


    override fun <T : ICte, R> cteColumn(
        cte: KClass<T>,
        select: KProperty1<T, R>
    ): IQueryColumnsBaseApi {
        this.ast.cte =              cte.findAnnotation<QBCte>()?.name;
        this.ast.cteAlias =         cte.findAnnotation<QBCte>()?.alias;
        this.ast.select =           select.findAnnotation<QBCteSelect>()?.name;
        this.ast.selectAlias =      select.findAnnotation<QBCteSelect>()?.alias;
        return this;
    }*/

    override fun tableAttribute(
        tableAttribute: String
    ): IQueryColumnsBaseApi {
        this.ast.tableAttribute =       tableAttribute;
        return this;
    }


    override fun tableColumn(
        tableAlias: String,
        columnName: String
    ): IQueryColumnsBaseApi {
        this.ast.tableAlias =       tableAlias;
        this.ast.column =           columnName;
        return this;
    }

    override fun cteColumn(
        cteAlias: String,
        selectName: String
    ): IQueryColumnsBaseApi {
        this.ast.cteAlias =       cteAlias;
        this.ast.select =         selectName;
        return this;
    }


}