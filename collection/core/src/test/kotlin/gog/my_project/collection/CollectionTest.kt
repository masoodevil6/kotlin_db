package gog.my_project.collection

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CollectionTest {
    @Test
    fun newCollectionHasZeroSize() {
        assertEquals(0, Collection<String>().size)
    }

    @Test
    fun addingOneElementSetsSizeToOne() {
        val collection = Collection<String>()

        collection.add("Ada")

        assertEquals(1, collection.size)
    }

    @Test
    fun addingMultipleElementsReportsTheirCount() {
        val collection = Collection<Int>()
        collection.add(1)
        collection.add(2)
        collection.add(3)

        assertEquals(3, collection.size)
    }

    @Test
    fun forEachVisitsElementsInInsertionOrder() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Grace")
        collection.add("Katherine")
        val visited = mutableListOf<String>()

        collection.forEach { visited.add(it) }

        assertEquals(listOf("Ada", "Grace", "Katherine"), visited)
    }

    @Test
    fun forEachOnEmptyCollectionInvokesActionZeroTimes() {
        val collection = Collection<String>()
        var invocations = 0

        collection.forEach { invocations++ }

        assertEquals(0, invocations)
    }

    @Test
    fun instancesMaintainIndependentContentsAndSizes() {
        val first = Collection<String>()
        val second = Collection<String>()
        first.add("Ada")
        first.add("Grace")
        second.add("Katherine")
        val firstValues = mutableListOf<String>()
        val secondValues = mutableListOf<String>()

        first.forEach { firstValues.add(it) }
        second.forEach { secondValues.add(it) }

        assertEquals(2, first.size)
        assertEquals(1, second.size)
        assertEquals(listOf("Ada", "Grace"), firstValues)
        assertEquals(listOf("Katherine"), secondValues)
    }

    @Test
    fun collectionPreservesElementReferencesAndValues() {
        val first = StringBuilder("Ada")
        val second = StringBuilder("Grace")
        val collection = Collection<StringBuilder>()
        collection.add(first)
        collection.add(second)
        val visited = mutableListOf<StringBuilder>()

        collection.forEach { visited.add(it) }

        assertSame(first, visited[0])
        assertSame(second, visited[1])
        assertEquals("Ada", visited[0].toString())
        assertEquals("Grace", visited[1].toString())

        first.append(" Lovelace")

        assertSame(first, visited[0])
        assertEquals("Ada Lovelace", visited[0].toString())
    }

    @Test
    fun filteringAnEmptyCollectionReturnsAnEmptyCollection() {
        val collection = Collection<String>()

        val filtered = collection.filter { true }

        assertEquals(0, filtered.size)
        assertEquals(0, collection.size)
    }

    @Test
    fun filteringWithNoMatchesReturnsAnEmptyCollection() {
        val collection = Collection<Int>()
        collection.add(1)
        collection.add(2)

        val filtered = collection.filter { it > 2 }

        assertEquals(0, filtered.size)
        assertEquals(2, collection.size)
    }

    @Test
    fun filteringWithAllMatchesReturnsAllElementsInOrder() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Grace")

        val filtered = collection.filter { true }

        assertEquals(listOf("Ada", "Grace"), valuesOf(filtered))
        assertEquals(2, filtered.size)
    }

    @Test
    fun filteringReturnsOnlyMatchingElements() {
        val collection = Collection<Int>()
        collection.add(1)
        collection.add(2)
        collection.add(3)
        collection.add(4)

        val filtered = collection.filter { it % 2 == 0 }

        assertEquals(listOf(2, 4), valuesOf(filtered))
    }

    @Test
    fun filteringPreservesInsertionOrderAmongMatches() {
        val collection = Collection<String>()
        collection.add("first-match")
        collection.add("skip")
        collection.add("second-match")
        collection.add("third-match")

        val filtered = collection.filter { it.endsWith("match") }

        assertEquals(listOf("first-match", "second-match", "third-match"), valuesOf(filtered))
    }

    @Test
    fun filteringDoesNotModifyTheOriginalCollection() {
        val collection = Collection<Int>()
        collection.add(1)
        collection.add(2)
        collection.add(3)

        collection.filter { it > 1 }

        assertEquals(listOf(1, 2, 3), valuesOf(collection))
        assertEquals(3, collection.size)
    }

    @Test
    fun filteringPreservesReferencesToMatchingObjects() {
        val first = StringBuilder("Ada")
        val skipped = StringBuilder("Skip")
        val last = StringBuilder("Grace")
        val collection = Collection<StringBuilder>()
        collection.add(first)
        collection.add(skipped)
        collection.add(last)

        val filtered = collection.filter { it !== skipped }
        val values = valuesOf(filtered)

        assertSame(first, values[0])
        assertSame(last, values[1])
    }

    @Test
    fun mappingAnEmptyCollectionReturnsAnEmptyCollection() {
        val collection = Collection<String>()

        val mapped = collection.map { it.length }

        assertEquals(0, mapped.size)
        assertEquals(0, collection.size)
    }

    @Test
    fun mappingPreservesElementOrder() {
        val collection = Collection<Int>()
        collection.add(3)
        collection.add(1)
        collection.add(2)

        val mapped = collection.map { it * 10 }

        assertEquals(listOf(30, 10, 20), valuesOf(mapped))
    }

    @Test
    fun mappingCanTransformElementsToAnotherType() {
        val collection = Collection<Int>()
        collection.add(7)
        collection.add(42)

        val mapped: ICollection<String> = collection.map { "id-$it" }

        assertEquals(listOf("id-7", "id-42"), valuesOf(mapped))
    }

    @Test
    fun mappingPreservesDuplicateResults() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Eve")

        val mapped = collection.map { it.length }

        assertEquals(listOf(3, 3), valuesOf(mapped))
    }

    @Test
    fun mappingDoesNotModifyTheOriginalCollection() {
        val collection = Collection<Int>()
        collection.add(1)
        collection.add(2)
        collection.add(3)

        collection.map { it * 2 }

        assertEquals(listOf(1, 2, 3), valuesOf(collection))
        assertEquals(3, collection.size)
    }

    @Test
    fun mappingTransformsEachElementExactlyOnceInIterationOrder() {
        val collection = Collection<Int>()
        collection.add(3)
        collection.add(1)
        collection.add(2)
        val transformedElements = mutableListOf<Int>()

        val mapped = collection.map { element ->
            transformedElements.add(element)
            element * 2
        }

        assertEquals(listOf(3, 1, 2), transformedElements)
        assertEquals(listOf(6, 2, 4), valuesOf(mapped))
    }

    @Test
    fun firstOrNullOnAnEmptyCollectionReturnsNull() {
        val collection = Collection<Int>()

        assertEquals(null, collection.firstOrNull { true })
    }

    @Test
    fun firstOrNullReturnsNullWhenNoElementMatches() {
        val collection = Collection<Int>()
        collection.add(1)
        collection.add(2)
        collection.add(3)

        assertEquals(null, collection.firstOrNull { it > 3 })
    }

    @Test
    fun firstOrNullReturnsAMatchingElement() {
        val collection = Collection<Int>()
        collection.add(1)
        collection.add(2)
        collection.add(3)

        assertEquals(2, collection.firstOrNull { it == 2 })
    }

    @Test
    fun firstOrNullReturnsOnlyTheFirstMatchingElement() {
        val collection = Collection<Int>()
        collection.add(2)
        collection.add(4)
        collection.add(6)

        assertEquals(2, collection.firstOrNull { it % 2 == 0 })
    }

    @Test
    fun firstOrNullPreservesTheMatchingElementReference() {
        val expected = StringBuilder("Ada")
        val collection = Collection<StringBuilder>()
        collection.add(StringBuilder("Skip"))
        collection.add(expected)

        assertSame(expected, collection.firstOrNull { it.toString() == "Ada" })
    }

    @Test
    fun firstOrNullStopsEvaluatingAfterTheFirstMatch() {
        val collection = Collection<Int>()
        collection.add(1)
        collection.add(2)
        collection.add(3)
        collection.add(4)
        val evaluated = mutableListOf<Int>()

        val result = collection.firstOrNull { element ->
            evaluated.add(element)
            element == 2
        }

        assertEquals(2, result)
        assertEquals(listOf(1, 2), evaluated)
    }

    @Test
    fun firstOrNullDoesNotModifyTheOriginalCollection() {
        val collection = Collection<Int>()
        collection.add(1)
        collection.add(2)
        collection.add(3)

        collection.firstOrNull { it == 2 }

        assertEquals(listOf(1, 2, 3), valuesOf(collection))
        assertEquals(3, collection.size)
    }

    @Test
    fun convertingAnEmptyCollectionReturnsAnEmptyList() {
        assertEquals(emptyList(), Collection<String>().toList())
    }

    @Test
    fun toListPreservesInsertionOrder() {
        val collection = Collection<String>()
        collection.add("Grace")
        collection.add("Ada")
        collection.add("Katherine")

        assertEquals(listOf("Grace", "Ada", "Katherine"), collection.toList())
    }

    @Test
    fun toListPreservesDuplicateElements() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Grace")
        collection.add("Ada")

        assertEquals(listOf("Ada", "Grace", "Ada"), collection.toList())
    }

    @Test
    fun toListReturnsExpectedSizeAndContents() {
        val collection = Collection<Int>()
        collection.add(4)
        collection.add(8)
        collection.add(15)

        val values = collection.toList()

        assertEquals(3, values.size)
        assertEquals(listOf(4, 8, 15), values)
    }

    @Test
    fun toListHasAnIndependentStructureFromTheCollection() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Grace")

        val values = collection.toList()
        collection.add("Katherine")

        assertEquals(listOf("Ada", "Grace"), values)
        assertEquals(3, collection.size)
    }

    @Test
    fun toListPreservesElementReferences() {
        val first = StringBuilder("Ada")
        val second = StringBuilder("Grace")
        val collection = Collection<StringBuilder>()
        collection.add(first)
        collection.add(second)

        val values = collection.toList()

        assertSame(first, values[0])
        assertSame(second, values[1])
    }

    @Test
    fun indexedAccessReturnsTheFirstElement() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Grace")

        assertEquals("Ada", collection[0])
    }

    @Test
    fun indexedAccessReturnsAMiddleElement() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Grace")
        collection.add("Katherine")

        assertEquals("Grace", collection[1])
    }

    @Test
    fun indexedAccessReturnsTheLastElement() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Grace")
        collection.add("Katherine")

        assertEquals("Katherine", collection[collection.size - 1])
    }

    @Test
    fun indexedAccessOnAnEmptyCollectionThrowsIndexOutOfBoundsException() {
        assertFailsWith<IndexOutOfBoundsException> {
            Collection<String>()[0]
        }
    }

    @Test
    fun indexedAccessWithANegativeIndexThrowsIndexOutOfBoundsException() {
        val collection = Collection<String>()
        collection.add("Ada")

        assertFailsWith<IndexOutOfBoundsException> {
            collection[-1]
        }
    }

    @Test
    fun indexedAccessAtSizeThrowsIndexOutOfBoundsException() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Grace")

        assertFailsWith<IndexOutOfBoundsException> {
            collection[collection.size]
        }
    }

    @Test
    fun indexedAccessPreservesTheElementReference() {
        val expected = StringBuilder("Ada")
        val collection = Collection<StringBuilder>()
        collection.add(expected)

        assertSame(expected, collection[0])
    }

    @Test
    fun indexedAccessDoesNotModifyTheCollection() {
        val collection = Collection<String>()
        collection.add("Ada")
        collection.add("Grace")
        val originalSize = collection.size

        collection[0]

        assertEquals(originalSize, collection.size)
        assertEquals(listOf("Ada", "Grace"), collection.toList())
    }

    private fun <T> valuesOf(collection: ICollection<T>): List<T> {
        val values = mutableListOf<T>()
        collection.forEach(values::add)
        return values
    }
}
