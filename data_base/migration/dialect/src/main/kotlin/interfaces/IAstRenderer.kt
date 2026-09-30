package gog.my_project.data_base.migration.dialect.interfaces

import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass

interface IAstRenderer<A: Any , DC: MigrationDataClass?> {

    fun render(
        ast: A,
        ctx: IRenderContext,
        dataClass: DC? = null
    ) : String?;

}