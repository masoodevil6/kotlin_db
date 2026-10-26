package gog.my_project.data_base.query.builder.ast.select_builder.cte

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.collection.ICollection
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import kotlin.test.Test
import kotlin.test.assertEquals

@QBQuery(name = "annotated_cte_declaration")
private class AnnotatedCteDeclaration : CteDeclaration<Unit, AnnotatedCteDeclaration>() {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { columnSelector("1") }; alias("value") } }
    }
}

private class ConventionQuery : CteDeclaration<Unit, ConventionQuery>() {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { columnSelector("1") }; alias("value") } }
    }
}

private class OverridingCteDeclaration : CteDeclaration<Unit, OverridingCteDeclaration>() {
    override fun createCte(params: Unit): Cte = error("compile-only override fixture")

    override fun execute(
        queryManager: IQueryBuilderExecutor,
        params: Unit,
    ): ExecuteResult<ICollection<OverridingCteDeclaration>> =
        error("compile-only override fixture")
}

class CteDeclarationTest {
    @Test
    fun `declaration builder uses annotated query name`() {
        assertEquals("annotated_cte_declaration", AnnotatedCteDeclaration().createCte(Unit).name)
    }

    @Test
    fun `declaration builder falls back to class convention`() {
        assertEquals("convention", ConventionQuery().createCte(Unit).name)
    }
}
