package gog.my_project.data_base.query.builder.ast.with

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.core.annotations.ctes.QBCte
import gog.my_project.data_base.core.managers.ctes.ICte
import gog.my_project.data_base.query.builder.interfaces.IQueryBuilder
import gog.my_project.data_base.query.builder.interfaces.with.IQueryToolsWithItem
import gog.my_project.data_base.query.definition.interfaces.with.IQueryDefinitionWithsItem
import kotlin.reflect.full.findAnnotation

class QueryToolsWithItem(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryToolsWithItem , IQueryDefinitionWithsItem
{

    /*private var withName : String? = null;
    private var withBody:  IQueryBuilder? = null



    *//* ==============================================================
    template
    ============================================================== *//*
    override fun getWithName(): String? {
        return withName;
    }

    override fun getWithBody(): IQueryBuilder? {
        return withBody;
    }



    *//* ==============================================================
    structure
    ============================================================== *//*
    override fun withName(withName: String): IQueryToolsWithItem {
        this.withName = withName;
        return this;
    }


    override fun withBody(blockWith: IQueryBuilder.() -> IQueryBuilder): IQueryToolsWithItem {
        val builder = QueryBuilder(params);
        this.withBody = builder.blockWith();
        return this;
    }*/



    private var withName : String? = null;
    private var withBody:  IQueryBuilder? = null



    /* ==============================================================
    Builder
    ============================================================== */
    override fun getWithName(): String? {
        return withName;
    }

    override fun getWithBody(): IQueryBuilder? {
        return withBody;
    }




    /* ==============================================================
      structure
    ============================================================== */
    override fun with(with: ICte): IQueryDefinitionWithsItem {
        val cteClass = with::class;

        val cteName = cteClass.findAnnotation<QBCte>()
            ?: error("CteName annotation missing on ${cteClass.simpleName}")

        this.withName = cteName.name
        //this.withBody = with.cteQuery(params);

        return this;
    }


}