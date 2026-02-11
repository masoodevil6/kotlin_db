package gog.my_project.data_base.query.builder.ast.table

import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.managers.models.IModel
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.table.IQueryTableApi
import gog.my_project.data_base.query.ast.interfaces.table.IQueryTableAst
import gog.my_project.data_base.query.ast.schema.table.QueryTableAst
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class QueryTableBuilder(
    override var ast: IQueryTableAst = QueryTableAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) : IQueryTableApi {

    override fun <T : IModel> table(
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
    }

}