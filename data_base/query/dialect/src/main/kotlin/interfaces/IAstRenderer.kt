package gog.my_project.data_base.query.renderer.interfaces

import gog.my_project.data_base.query.dialect.data_class.QueryDataClass


interface IAstRenderer<T: Any , DC: QueryDataClass?> {

    fun render(
        ast: T,
        ctx: IRenderContext ,
        dataClass: DC? = null
    ) : String?;

}