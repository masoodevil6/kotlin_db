package gog.my_project.data_base.query.dialect.nodes.select_nodes.where

import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst
import gog.my_project.data_base.query.dialect.data_class.select_data.where.QueryWhereData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryWhereCapability : IAstRenderer<IQueryWhereAst, QueryWhereData> {
}