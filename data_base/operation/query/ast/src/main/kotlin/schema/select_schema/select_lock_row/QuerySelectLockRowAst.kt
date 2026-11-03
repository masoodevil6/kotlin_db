package gog.my_project.data_base_query.ast.schema.select_schema.select_lock_row

import gog.my_project.data_base_query.ast.interfaces.select_interface.select_lock_row.IQuerySelectLockRowAst
import gog.my_project.data_base_query.ast.metadata.select.select_lock_row.QuerySelectLockRowAstMetadata

class QuerySelectLockRowAst(
    override var metadata: QuerySelectLockRowAstMetadata = QuerySelectLockRowAstMetadata()
) : IQuerySelectLockRowAst {



}