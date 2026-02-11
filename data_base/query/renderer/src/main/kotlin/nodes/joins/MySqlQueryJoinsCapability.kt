package gog.my_project.data_base.query.renderer.nodes.joins

import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst
import gog.my_project.data_base.query.dialect.data_class.joins.QueryJoinsData
import gog.my_project.data_base.query.dialect.data_class.joins_item.QueryJoinsItemData
import gog.my_project.data_base.query.dialect.nodes.joins.IQueryJoinsCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryJoinsCapability: IQueryJoinsCapability {

    override fun render(
        ast: IQueryJoinsAst,
        ctx: IRenderContext ,
        dataClass: QueryJoinsData?
    ): String? {

        val joinList: List<IQueryJoinsItemAst> = ast.joins;

        if (joinList.size > 0) {
            var joinsStr = "";
            for ((index, join) in joinList.withIndex()){
                val itemJoin = ctx.registry.render(join , ctx.dialect , QueryJoinsItemData());
                if (itemJoin != null){
                    joinsStr +=  " ${itemJoin} ";
                }
            }
            return " $joinsStr ";
        }

        return null;
    }

}