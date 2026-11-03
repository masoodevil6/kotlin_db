package gog.my_project.data_base_schema.dialect.interfaces

import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass

interface IAstRenderer<A: Any , DC: SchemaDataClass?> {

    fun render(
        ast: A,
        ctx: IRenderContext,
        dataClass: DC? = null
    ) : String?;

}