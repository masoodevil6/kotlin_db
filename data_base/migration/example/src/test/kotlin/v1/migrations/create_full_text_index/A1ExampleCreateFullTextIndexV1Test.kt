package gog.my_project.data_base.migration.example.v1.migrations.create_full_text_index

import gog.my_project.data_base.migration.ast.schema.create_full_text_index.MigrationCreateFullTextIndexAst
import gog.my_project.data_base.migration.builder.ast.create_full_text_index.MigrationCreateFullTextIndexBuilder
import gog.my_project.data_base.migration.renderer.dialects.MySqlDialect
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.Test




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
        val migration = MigrationCreateFullTextIndexBuilder().apply {
            tableName(" Posts ")
            name(" Ft_Posts_Title ")
            columns(" Title ")
        }

        assertEquals(listOf("Title"), migration.ast.metadata.columnNames)
        assertEquals(
            "CREATE FULLTEXT INDEX `Ft_Posts_Title` ON `Posts` (`Title`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun laterColumnsCallReplacesEarlierListAndKeepsOrder() {
        val migration = MigrationCreateFullTextIndexBuilder().apply {
            tableName("posts")
            name("ft_posts_content")
            columns("old_title", "old_body")
            columns("body", "title")
        }

        assertEquals(listOf("body", "title"), migration.ast.metadata.columnNames)
        assertEquals(
            "CREATE FULLTEXT INDEX `ft_posts_content` ON `posts` (`body`, `title`)",
            dialect.render(migration.ast),
        )
    }

    @Test
    fun rendersBlankAndDuplicateColumnsAndKeepsEmptyListAsStructuralError() {
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateFullTextIndexBuilder().apply { tableName("posts"); name("ft_posts") }.ast)
        }
        assertEquals(
            "CREATE FULLTEXT INDEX `ft_posts` ON `posts` (``)",
            dialect.render(MigrationCreateFullTextIndexBuilder().apply { tableName("posts"); name("ft_posts"); columns(" ") }.ast),
        )
        assertEquals(
            "CREATE FULLTEXT INDEX `ft_posts` ON `posts` (`title`, `TITLE`)",
            dialect.render(MigrationCreateFullTextIndexBuilder().apply { tableName("posts"); name("ft_posts"); columns("title", " TITLE ") }.ast),
        )
    }

    @Test
    fun rendererValidatesDirectAstBeforeBuildingSql() {
        assertFailsWith<IllegalArgumentException> {
            dialect.render(MigrationCreateFullTextIndexAst().apply {
                metadata.tableName = "posts"
                metadata.indexName = "ft_posts"
                metadata.columnNames = emptyList()
            })
        }
        assertEquals(
            "CREATE FULLTEXT INDEX `ft_posts` ON `posts` (`title`, ` TITLE `)",
            dialect.render(MigrationCreateFullTextIndexAst().apply {
                metadata.tableName = "posts"
                metadata.indexName = "ft_posts"
                metadata.columnNames = listOf("title", " TITLE ")
            }),
        )
    }

    @Test
    fun quotesEscapesAndDoesNotAddOtherIndexOptions() {
        val sql = MigrationCreateFullTextIndexBuilder().apply {
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
