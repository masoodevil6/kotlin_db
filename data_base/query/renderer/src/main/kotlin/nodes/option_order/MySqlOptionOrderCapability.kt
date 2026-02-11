package gog.my_project.data_base.query.renderer.nodes.option_order

import gog.my_project.data_base.query.ast.interfaces.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.dialect.data_class.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.dialect.data_class.option_order.QueryOptionOrderData
import gog.my_project.data_base.query.dialect.nodes.option_order.IQueryOptionOrderCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlOptionOrderCapability : IQueryOptionOrderCapability {

    override fun render(
        ast: IQueryOptionOrderAst,
        ctx: IRenderContext ,
        dataClass: QueryOptionOrderData?
    ): String? {

        var columnsList = ast.orderByList;
        var orderType = ast.orderType;

        if (columnsList.isNotEmpty()) {
            var optionOrderStr = "";
            for ((index, column) in columnsList.withIndex()){
                val columnStr =
                    ctx.registry.render(
                        column ,
                        ctx.dialect ,
                        QueryColumnBaseData()
                    );
                if (columnStr != null) {
                    optionOrderStr += " $columnStr"
                }
            }

            var strExt = " ${ctx.dialect._prefixOrder} $optionOrderStr "
            if (orderType != null) {
                strExt += " $orderType ";
            }

            return strExt
        }

        return null;
    }

}