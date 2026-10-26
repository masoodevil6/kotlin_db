package gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.concat

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.concat.QueryColumnMethodConcatData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnMethodConcatCapability : IAstRenderer<IQueryColumnMethodConcatAst, QueryColumnMethodConcatData> {

}