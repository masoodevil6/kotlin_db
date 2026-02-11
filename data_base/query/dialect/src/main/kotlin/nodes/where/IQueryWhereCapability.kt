package gog.my_project.data_base.query.dialect.nodes.where

import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst
import gog.my_project.data_base.query.dialect.data_class.where.QueryWhereData
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer

interface IQueryWhereCapability : IAstRenderer<IQueryWhereAst , QueryWhereData> {
}