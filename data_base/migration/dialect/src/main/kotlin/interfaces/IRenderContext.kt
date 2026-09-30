package gog.my_project.data_base.migration.dialect.interfaces


interface IRenderContext {
    val registry:  IRendererRegistry;
    val dialect:   ISqlDialect;
}