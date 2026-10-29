package gog.my_project.data_base.query.renderer.nodes.select_nodes.select_lock_row

import gog.my_project.data_base.query.ast.interfaces.select_interface.select_lock_row.IQuerySelectLockRowAst
import gog.my_project.data_base.query.dialect.data_class.select_data.select_lock_row.QuerySelectLockRowData
import gog.my_project.data_base.query.dialect.nodes.select_nodes.select_lock_row.IQuerySelectLockRowCapability
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext

class MySqlQuerySelectLockRowCapability :
    IQuerySelectLockRowCapability , IParameterOccurrenceAwareRenderer  {

    override fun render(
        ast: IQuerySelectLockRowAst,
        ctx: IRenderContext,
        dataClass: QuerySelectLockRowData?
    ): String? {
        return " ${ctx.dialect._prefixSelectLock} ";
    }

}