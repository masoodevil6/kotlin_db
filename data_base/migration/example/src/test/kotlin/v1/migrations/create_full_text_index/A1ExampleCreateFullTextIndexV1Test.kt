package gog.my_project.data_base.migration.example.v1.migrations.create_full_text_index

import gog.my_project.data_base.migration.ast.schema.create_full_text_index.MigrationCreateFullTextIndexAst
import gog.my_project.data_base.migration.builder.createFullTextIndex
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class A1ExampleCreateFullTextIndexV1Test {
    private val dialect = MySqlDialect()

    @Test
    fun rendersMultiColumnFullTextIndex() {
        assertEquals(
            "CREATE FULLTEXT INDEX `ft_posts_title_body` ON `posts` (`title`, `body`)",
            dialect.render(A1ExampleCreateFullTextIndexV1().migration().ast),
        )
    }

    @Test
    fun acceptsSingleColumnAndPreservesTrimmedCasing() {
        val migration = createFullTextIndex {
            tableName(" Posts ")
            name(" Ft_Posts_Title ")
            columns(" Title ")
        }

        assertEquals(listOf("Title"), migration.ast.columnNames)
        assertEquals(
            "CREATE FULLTEXT INDEX `Ft_Posts_Title` ON `Posts` (`Title`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun laterColumnsCallReplacesEarlierListAndKeepsOrder() {
        val migration = createFullTextIndex {
            tableName("posts")
            name("ft_posts_content")
            columns("old_title", "old_body")
            columns("body", "title")
        }

        assertEquals(listOf("body", "title"), migration.ast.columnNames)
        assertEquals(
            "CREATE FULLTEXT INDEX `ft_posts_content` ON `posts` (`body`, `title`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun rejectsMissingBlankAndCaseInsensitiveDuplicateColumns() {
        assertFailsWith<IllegalArgumentException> {
            createFullTextIndex { tableName("posts"); name("ft_posts") }
        }
        assertFailsWith<IllegalArgumentException> {
            createFullTextIndex { tableName("posts"); name("ft_posts"); columns(" ") }
        }
        assertFailsWith<IllegalArgumentException> {
            createFullTextIndex { tableName("posts"); name("ft_posts"); columns("title", " TITLE ") }
        }
    }

    @Test
    fun rendererValidatesDirectAstBeforeBuildingSql() {
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateFullTextIndexAst().apply {
                tableName = "posts"
                indexName = "ft_posts"
                columnNames = emptyList()
            })
        }
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateFullTextIndexAst().apply {
                tableName = "posts"
                indexName = "ft_posts"
                columnNames = listOf("title", " TITLE ")
            })
        }
    }

    @Test
    fun quotesEscapesAndDoesNotAddOtherIndexOptions() {
        val sql = createFullTextIndex {
            tableName("order")
            name("ft`posts")
            columns("select", "body`text")
        }.let { dialect.render(it.ast)!! }

        assertEquals("CREATE FULLTEXT INDEX `ft``posts` ON `order` (`select`, `body``text`)", sql)
        assertFalse(sql.contains("UNIQUE"))
        assertFalse(sql.contains("WITH PARSER"))
        assertFalse(sql.contains("IF NOT EXISTS"))
    }
}
