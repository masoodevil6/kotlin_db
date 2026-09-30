package gog.my_project.data_base.query.renderer.nodes.select_nodes.option_offset

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_offset.IQueryOptionOffsetAst
import gog.my_project.data_base.query.dialect.data_class.select_data.option_offset.QueryOptionOffsetData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.option_offset.IQueryOptionOffsetCapability
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlOptionOffsetCapability :
    IQueryOptionOffsetCapability {

    override fun render(
        ast:         IQueryOptionOffsetAst,
        ctx:         IRenderContext,
        dataClass:   QueryOptionOffsetData?
    ): String? {

        val offsetNumber = ast.pageOffset;
        if (offsetNumber != null) {
            return " ${ctx.dialect._prefixOffset} $offsetNumber "
        }

        return null;
    }

}