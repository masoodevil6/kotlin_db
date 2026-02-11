package gog.my_project.data_base.query.renderer.nodes.query

import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.dialect.data_class.joins.QueryJoinsData
import gog.my_project.data_base.query.dialect.data_class.option_group.QueryOptionGroupData
import gog.my_project.data_base.query.dialect.data_class.option_limit.QueryOptionLimitData
import gog.my_project.data_base.query.dialect.data_class.option_offset.QueryOptionOffsetData
import gog.my_project.data_base.query.dialect.data_class.option_order.QueryOptionOrderData
import gog.my_project.data_base.query.dialect.data_class.query_render_select.IQueryRenderSelectData
import gog.my_project.data_base.query.dialect.data_class.select.QuerySelectData
import gog.my_project.data_base.query.dialect.data_class.table.QueryTableData
import gog.my_project.data_base.query.dialect.data_class.where.QueryWhereData
import gog.my_project.data_base.query.dialect.data_class.withs.QueryWithsData
import gog.my_project.data_base.query.dialect.nodes.query.IQueryRenderSelectCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryCapability : IQueryRenderSelectCapability {

    override fun render(
        ast: IQueryRenderSelectAst,
        ctx: IRenderContext ,
        dataClass: IQueryRenderSelectData?
    ): String?
    {

        val queryBuilderWiths =        ast.withs;
        val queryBuilderSelect =       ast.select;
        val queryBuilderTable =        ast.table;
        val queryBuilderJoins =        ast.joins;
        val queryBuilderWhere =        ast.where;
        val queryBuilderOptionLimit =  ast.optionLimit;
        val queryBuilderOptionOffset = ast.optionOffset;
        val queryBuilderOptionGroup =  ast.optionGroup;
        val queryBuilderOptionOrder =  ast.optionOrder;

        return  " ${ctx.registry.render(queryBuilderWiths        , ctx.dialect  , QueryWithsData(  _withPrefix = true))       } " +
                " ${ctx.registry.render(queryBuilderSelect       , ctx.dialect  , QuerySelectData( _withPrefix = true))       } " +
                " ${ctx.registry.render(queryBuilderTable        , ctx.dialect  , QueryTableData(  _withPrefix = true))       } " +
                " ${ctx.registry.render(queryBuilderJoins        , ctx.dialect  , QueryJoinsData())                           } " +
                " ${ctx.registry.render(queryBuilderWhere        , ctx.dialect  , QueryWhereData())                           } " +
                " ${ctx.registry.render(queryBuilderOptionGroup  , ctx.dialect  , QueryOptionGroupData())                     } " +
                " ${ctx.registry.render(queryBuilderOptionOrder  , ctx.dialect  , QueryOptionOrderData())                     } " +
                " ${ctx.registry.render(queryBuilderOptionLimit  , ctx.dialect  , QueryOptionLimitData())                     } " +
                " ${ctx.registry.render(queryBuilderOptionOffset , ctx.dialect  , QueryOptionOffsetData())                    } ";

    }

}