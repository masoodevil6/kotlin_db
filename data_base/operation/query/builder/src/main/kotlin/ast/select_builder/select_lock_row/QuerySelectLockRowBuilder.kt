package gog.my_project.data_base_query.builder.ast.select_builder.select_lock_row

import gog.my_project.data_base_query.api.interfaces.api.select_api.select_lock_row.IQuerySelectLockRowApi
import gog.my_project.data_base_query.ast.interfaces.select_interface.select_lock_row.IQuerySelectLockRowAst

class QuerySelectLockRowBuilder(
    override var ast: IQuerySelectLockRowAst
) : IQuerySelectLockRowApi {

}