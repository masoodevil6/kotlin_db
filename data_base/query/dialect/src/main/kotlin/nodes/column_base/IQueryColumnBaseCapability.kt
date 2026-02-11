package gog.my_project.data_base.query.dialect.nodes.column_base

import gog.my_project.data_base.query.ast.interfaces.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.dialect.data_class.column_base.QueryColumnBaseData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryColumnBaseCapability : IAstRenderer<IQueryColumnsBaseAst , QueryColumnBaseData> {

}