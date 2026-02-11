package gog.my_project.data_base.query.renderer.nodes.option_limit

import gog.my_project.data_base.query.ast.interfaces.option_limit.IQueryOptionLimitAst
import gog.my_project.data_base.query.dialect.data_class.option_limit.QueryOptionLimitData
import gog.my_project.data_base.query.dialect.nodes.option_limit.IQueryOptionLimitCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQueryOptionLimitCapability : IQueryOptionLimitCapability {

    override fun render(
        ast: IQueryOptionLimitAst ,
        ctx: IRenderContext ,
        dataClass: QueryOptionLimitData?
    ): String? {

        val limitNumber = ast.pageLimit;
        if (limitNumber != null) {
            return " ${ctx.dialect._prefixLimit} $limitNumber "
        }

        return null
    }

}