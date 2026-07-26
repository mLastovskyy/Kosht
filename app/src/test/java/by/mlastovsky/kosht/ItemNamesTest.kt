package by.mlastovsky.kosht

import by.mlastovsky.kosht.util.ItemNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemNamesTest {

    @Test
    fun `normalize settles case and spacing`() {
        assertEquals("Молоко", ItemNames.normalize("  МОЛОКО  "))
        assertEquals("Молоко 3.2%", ItemNames.normalize("молоко   3.2%"))
        assertEquals("Хлеб", ItemNames.normalize("хлеб\n"))
    }

    @Test
    fun `normalize refuses a line with no name in it`() {
        assertNull(ItemNames.normalize("   "))
        assertNull(ItemNames.normalize("---"))
        assertNull(ItemNames.normalize("*"))
    }

    @Test
    fun `normalize cuts a description down to a name`() {
        val long = "a".repeat(80)
        assertEquals(ItemNames.MAX_LENGTH, ItemNames.normalize(long)?.length)
    }

    @Test
    fun `key groups the spellings of one product`() {
        val key = ItemNames.key("Молоко")
        assertEquals(key, ItemNames.key("молоко"))
        assertEquals(key, ItemNames.key("  МОЛОКО "))
        assertEquals(key, ItemNames.key("Молоко"))

        assertEquals(ItemNames.key("Хлеб бородинский"), ItemNames.key("хлеб   Бородинский"))
    }

    @Test
    fun `key does not care about yo`() {
        assertEquals(ItemNames.key("Мёд"), ItemNames.key("мед"))
        assertEquals(ItemNames.key("Ёлка"), ItemNames.key("елка"))
    }

    @Test
    fun `key keeps different products apart`() {
        assertNotEquals(ItemNames.key("Молоко"), ItemNames.key("Молоток"))
        assertNotEquals(ItemNames.key("Хлеб"), ItemNames.key("Хлебцы"))
    }

    @Test
    fun `key and normalize agree on what is the same`() {
        val spellings = listOf("Кофе", "кофе", "КОФЕ  ", " кофе ")
        val keys = spellings.map { ItemNames.key(it) }.distinct()
        val names = spellings.mapNotNull { ItemNames.normalize(it) }.distinct()
        assertEquals(1, keys.size)
        assertEquals(1, names.size)
    }
}
