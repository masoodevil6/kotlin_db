package gog.my_project.data_base.query.ast.schema.select_schema.option_partition

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAstPart
import gog.my_project.data_base.query.ast.metadata.select.option_partition.QueryOptionPartitionAstMetadata

class QueryOptionPartitionAst(
    override var metadata: QueryOptionPartitionAstMetadata = QueryOptionPartitionAstMetadata(),
) : IQueryOptionPartitionAst {

    override val parts: MutableList<IQueryOptionPartitionAstPart> = mutableListOf()

}