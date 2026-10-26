package gog.my_project.data_base.query.executer.manager

import gog.my_project.collection.Collection as ProjectCollection
import gog.my_project.collection.ICollection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.executer.result.toCollection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ExecuteResultCollectionExtensionsTest {

    @Test
    fun `toCollection returns the same successful collection instance`() {
        val collection: ICollection<String> = ProjectCollection<String>().apply {
            add("typed row")
        }
        val result: ExecuteResult<ICollection<String>> = ExecuteResult.Success(collection)

        val extracted: ICollection<String> = result.toCollection()

        assertSame(collection, extracted)
        assertEquals(listOf("typed row"), extracted.toList())
    }

    @Test
    fun `toCollection returns a real empty collection`() {
        val empty: ICollection<Int> = ProjectCollection()
        val result: ExecuteResult<ICollection<Int>> = ExecuteResult.Success(empty)

        val extracted: ICollection<Int> = result.toCollection()

        assertSame(empty, extracted)
        assertEquals(0, extracted.size)
    }

    @Test
    fun `toCollection rethrows the original failure`() {
        val original = IllegalArgumentException("query failed")
        val result: ExecuteResult<ICollection<String>> = ExecuteResult.Failure(original)

        val thrown = assertFailsWith<IllegalArgumentException> {
            result.toCollection()
        }

        assertSame(original, thrown)
    }

    @Test
    fun `toCollection rejects a successful result without a collection`() {
        val result: ExecuteResult<ICollection<String>> = ExecuteResult.Success(null)

        val thrown = assertFailsWith<IllegalStateException> {
            result.toCollection()
        }

        assertEquals("Successful result contains no collection", thrown.message)
    }

    @Test
    fun `toCollection keeps the ICollection element type at compile time`() {
        val source: ICollection<Int> = ProjectCollection<Int>().apply {
            add(17)
        }
        val result: ExecuteResult<ICollection<Int>> = ExecuteResult.Success(source)

        val extracted: ICollection<Int> = result.toCollection()

        assertEquals(17, extracted[0])
    }
}
