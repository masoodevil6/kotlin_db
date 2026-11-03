package gog.my_project.data_base_query.dialect.nodes.select_nodes.option_partition

import gog.my_project.data_base_query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAst
import gog.my_project.data_base_query.dialect.data_class.select_data.option_partition.QueryOptionPartitionData
import gog.my_project.data_base_query.renderer.interfaces.IAstRenderer

interface IQueryOptionPartitionCapability   : IAstRenderer<IQueryOptionPartitionAst, QueryOptionPartitionData>  {
}