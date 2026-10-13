package gog.my_project.data_base.query.renderer.interfaces

import gog.my_project.data_base.query.dialect.interfaces.ISqlDialect

interface IRenderContext {
    val registry:  IRendererRegistry;
    val dialect:   ISqlDialect;

    /** Ordered parameter references captured by an opt-in render session. */
    val parameterOccurrences: MutableList<Any>?
        get() = null
}
