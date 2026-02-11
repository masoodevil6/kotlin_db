package gog.my_project.data_base.query.builder.ast.withs_item

import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.withs_item.IQueryWithsItemApi
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.ast.schema.withs_item.QueryWithsItemAst
import kotlin.reflect.full.findAnnotation

class QueryWithsItemBuilder(
    override var ast: IQueryWithsItemAst = QueryWithsItemAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
): IQueryWithsItemApi {

    override fun with(
        with: ICte
    ): IQueryWithsItemApi
    {
        val cteClass = with::class;

        val cteName = cteClass.findAnnotation<QBCte>()
            ?: error("CteName annotation missing on ${cteClass.simpleName}")

        this.ast.withName = cteName.name
        //this.withBody = with.cteQuery(params);

        return this;
    }

}