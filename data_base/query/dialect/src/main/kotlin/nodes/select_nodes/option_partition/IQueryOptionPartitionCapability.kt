package gog.my_project.data_base.query.dialect.nodes.select_nodes.option_partition

import gog.my_project.data_base.query.ast.interfaces.select_interface.option_partition.IQueryOptionPartitionAst
import gog.my_project.data_base.query.dialect.data_class.select_data.option_partition.QueryOptionPartitionData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryOptionPartitionCapability   : IAstRenderer<IQueryOptionPartitionAst, QueryOptionPartitionData>  {
}