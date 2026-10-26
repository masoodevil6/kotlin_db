package gog.my_project.data_base.query.dialect.nodes.select_nodes.column_method.sum

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.sum.IQueryColumnMethodSumAst
import gog.my_project.data_base.query.dialect.data_class.select_data.column_method.sum.QueryColumnMethodSumData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnMethodSumCapability : IAstRenderer<IQueryColumnMethodSumAst, QueryColumnMethodSumData>
