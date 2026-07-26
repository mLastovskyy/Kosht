package by.mlastovsky.kosht.data

import by.mlastovsky.kosht.R

object ItemSuggestions {

    private val byCategoryKey: Map<String, List<Int>> = mapOf(
        "groceries" to listOf(
            R.string.item_seed_bread,
            R.string.item_seed_milk,
            R.string.item_seed_coffee,
            R.string.item_seed_vegetables,
            R.string.item_seed_meat,
            R.string.item_seed_sweets
        ),
        "housing" to listOf(
            R.string.item_seed_rent,
            R.string.item_seed_utilities,
            R.string.item_seed_internet,
            R.string.item_seed_repairs
        ),
        "transport" to listOf(
            R.string.item_seed_fuel,
            R.string.item_seed_public_transport,
            R.string.item_seed_taxi,
            R.string.item_seed_parking
        ),
        "health" to listOf(
            R.string.item_seed_pharmacy,
            R.string.item_seed_doctor,
            R.string.item_seed_dentist
        ),
        "subscriptions" to listOf(
            R.string.item_seed_mobile,
            R.string.item_seed_streaming,
            R.string.item_seed_cloud
        ),
        "entertainment" to listOf(
            R.string.item_seed_cinema,
            R.string.item_seed_concert,
            R.string.item_seed_games
        ),
        "education" to listOf(
            R.string.item_seed_courses,
            R.string.item_seed_books
        ),
        "cafe" to listOf(
            R.string.item_seed_coffee,
            R.string.item_seed_lunch,
            R.string.item_seed_dinner
        )
    )

    fun forCategory(key: String?): List<Int> = key?.let { byCategoryKey[it] }.orEmpty()
}
