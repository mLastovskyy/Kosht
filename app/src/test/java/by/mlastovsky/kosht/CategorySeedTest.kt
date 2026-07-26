package by.mlastovsky.kosht

import by.mlastovsky.kosht.data.CategorySeed
import by.mlastovsky.kosht.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategorySeedTest {

    @Test
    fun `seed keys are unique`() {
        val keys = CategorySeed.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `fallback categories exist for both types`() {

        assertTrue(CategorySeed.all.any {
            it.key == "other_expense" && it.type == TransactionType.EXPENSE
        })
        assertTrue(CategorySeed.all.any {
            it.key == "other_income" && it.type == TransactionType.INCOME
        })
    }

    @Test
    fun `every seed category has an icon and a visible color`() {
        CategorySeed.all.forEach { seed ->
            assertTrue("iconKey blank for ${seed.key}", seed.iconKey.isNotBlank())
            assertTrue("alpha missing for ${seed.key}", (seed.colorArgb ushr 24) == 0xFFL)
        }
    }

    @Test
    fun `both transaction types have categories`() {
        assertTrue(CategorySeed.all.any { it.type == TransactionType.EXPENSE })
        assertTrue(CategorySeed.all.any { it.type == TransactionType.INCOME })
    }
}
