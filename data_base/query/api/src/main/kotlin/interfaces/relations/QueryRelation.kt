package gog.my_project.data_base.query.api.interfaces.relations

/**
 * A query definition whose SELECT aliases are its SQL-visible outputs.
 * Implementations use reference identity; [name] is only its SQL CTE name.
 */
interface QueryRelation {
    val name: String
    val definition: QueryDefinition

    /** Relations consumed by this definition, in source dependency order. */
    val dependencies: List<QueryRelation> get() = emptyList()
}
