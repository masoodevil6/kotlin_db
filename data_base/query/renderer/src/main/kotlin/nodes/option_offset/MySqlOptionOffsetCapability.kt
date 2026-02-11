package gog.my_project.data_base.query.renderer.nodes.option_offset

import gog.my_project.data_base.query.ast.interfaces.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.dialect.data_class.option_offset.QueryOptionOffsetData
import gog.my_project.data_base.query.dialect.nodes.option_offset.IQueryOptionOffsetCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlOptionOffsetCapability : IQueryOptionOffsetCapability {

    override fun render(
        ast: IQueryOptionOffsetAst ,
        ctx: IRenderContext ,
        dataClass: QueryOptionOffsetData?
    ): String? {

        val offsetNumber = ast.pageOffset;
        if (offsetNumber != null) {
            return " ${ctx.dialect._prefixOffset} $offsetNumber "
        }

        return null;
    }

}