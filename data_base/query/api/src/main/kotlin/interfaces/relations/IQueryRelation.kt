package gog.my_project.data_base.query.api.interfaces.relations

/** Developer-facing declaration for a named, typed QueryRelation. */
interface IQueryRelation<P> {
    val relationName: String

    fun queryRelation(params: P): QueryRelation
}
