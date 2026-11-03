package gog.my_project.data_base_schema.dialect.interfaces


interface IRenderContext {
    val registry:  IRendererRegistry;
    val dialect:   ISqlDialect;
}