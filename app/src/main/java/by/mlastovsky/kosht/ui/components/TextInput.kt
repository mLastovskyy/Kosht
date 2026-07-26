package by.mlastovsky.kosht.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization

/**
 * Keyboard behaviour shared by the app's text fields. Free text reads better
 * when it starts with a capital, and typing one by hand on a phone is a
 * needless extra tap — so the keyboard offers it and the user can still
 * backspace to lower case.
 */
object TextInput {

    /** Notes, titles, search: the first letter of a sentence comes up capital. */
    val Sentence = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

    /** People, shops, accounts: every word starts with a capital. */
    val Name = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
}
