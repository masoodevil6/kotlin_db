package gog.my_project.data_base.query.renderer.nodes.withs

import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.dialect.data_class.withs.QueryWithsData
import gog.my_project.data_base.query.dialect.data_class.withs_item.QueryWithsItemData
import gog.my_project.data_base.query.dialect.nodes.withs.IQueryWithsCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryWithsCapability : IQueryWithsCapability {

    override fun render(
        ast: IQueryWithsAst,
        ctx: IRenderContext ,
        dataClass: QueryWithsData?
    ): String? {

        val withList:  List<IQueryWithsItemAst> = ast.withs;
        if (withList.size > 0) {
            var withStr = "";
            if (dataClass?._withPrefix == true){
                withStr = "  ${ctx.dialect._prefixWiths} "
            }
            for ((index, with) in withList.withIndex()){
                val itemWith =
                    ctx.registry.render(
                        with ,
                        ctx.dialect ,
                        QueryWithsItemData()
                    );
                if (itemWith != null) {
                    withStr +=  " ${itemWith}";
                    if (index < withList.size - 1){
                        withStr += ","
                    }
                }
            }
            return withStr ;
        }

        return null;

    }

}