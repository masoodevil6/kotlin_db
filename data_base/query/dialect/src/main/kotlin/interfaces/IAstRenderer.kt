package gog.my_project.data_base.query.renderer.interfaces

import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.dialect.data_class.QueryDataClass


interface IAstRenderer<A: Any , DC: QueryDataClass?> {

    fun render(
        ast: A,
        ctx: IRenderContext ,
        dataClass: DC? = null
    ) : String?;

}