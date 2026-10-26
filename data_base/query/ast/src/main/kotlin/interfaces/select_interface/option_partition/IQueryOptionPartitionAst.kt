package gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.metadata.select.option_partition.QueryOptionPartitionAstMetadata

interface IQueryOptionPartitionAst: IQueryAst<QueryOptionPartitionAstMetadata>  {

    val parts: MutableList<IQueryOptionPartitionAstPart>

}