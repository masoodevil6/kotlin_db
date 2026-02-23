package gog.my_project.data_base.query.builder.ast.select_builder.table

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class QueryTableBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryTableAst = QueryTableAst(),
) : gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi {

    /*override fun <T : IModelBase> table(
        table: KClass<T>
    ): IQueryTableApi
    {
        this.ast.table = table.findAnnotation<QBTable>()?.name;
        this.ast.tableAlias = table.findAnnotation<QBTable>()?.alias;
        return this;
    }

    override fun <T : ICte> cte(
        cte: KClass<T>
    ): IQueryTableApi
    {
        this.ast.cte = cte.findAnnotation<QBCte>()?.name;
        this.ast.cteAlias = cte.findAnnotation<QBCte>()?.alias;
        return this;
    }*/

    override fun table(
        table: String ,
        alias: String
    ): IQueryTableApi {
        this.ast.table =      table;
        this.ast.tableAlias = alias;
        return this;
    }

    override fun table(table: String): IQueryTableApi {
        this.ast.table =      table;
        return this;
    }

    override fun cte(
        cte: String,
        alias: String
    ): IQueryTableApi {
        this.ast.cte =      cte;
        this.ast.cteAlias = alias;
        return this;
    }

    override fun cte(cte: String): IQueryTableApi {
        this.ast.cte =      cte;
        return this;
    }

}