package gog.my_project.data_base.query.renderer.nodes.where

import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst
import gog.my_project.data_base.query.dialect.data_class.conditions_group.QueryConditionsGroupsData
import gog.my_project.data_base.query.dialect.data_class.where.QueryWhereData
import gog.my_project.data_base.query.dialect.nodes.where.IQueryWhereCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryWhereCapability : IQueryWhereCapability {

    override fun render(
        ast: IQueryWhereAst,
        ctx: IRenderContext ,
        dataClass: QueryWhereData?
    ): String? {

        val conditionGroup = ast.condition;
        val conditionStr =
            ctx.registry.render(
                conditionGroup ,
                ctx.dialect ,
                QueryConditionsGroupsData( _hasLogical = false )
            );
        if (conditionStr != null) {
            return  " ${ctx.dialect._prefixWhere} $conditionStr " ;
        }

        return  null ;

    }

}