package gog.my_project.data_base.query.api.interfaces.relations

/** Developer-facing declaration for a typed Query. */
interface ICteDeclaration<P> {
    fun createCte(params: P): Cte

    operator fun invoke(params: P): Cte = createCte(params)
}
