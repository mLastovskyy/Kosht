package by.mlastovsky.kosht.ui.awards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EmojiFlags
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PriceCheck
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import by.mlastovsky.kosht.R

object AwardVisuals {

    fun icon(key: String): ImageVector = when (key) {
        "first_steps" -> Icons.Rounded.Star
        "income_first" -> Icons.Rounded.Payments
        "ten" -> Icons.Rounded.CheckCircle
        "scanner" -> Icons.Rounded.CameraAlt
        "saver" -> Icons.Rounded.Savings
        "first_goal" -> Icons.Rounded.EmojiFlags
        "streak7" -> Icons.Rounded.LocalFireDepartment
        "surplus" -> Icons.AutoMirrored.Rounded.TrendingUp
        "goal_done" -> Icons.Rounded.Flag
        "challenge_done" -> Icons.Rounded.TaskAlt
        "night_owl" -> Icons.Rounded.NightsStay
        "debt_closed" -> Icons.Rounded.PriceCheck
        "photo10" -> Icons.Rounded.PhotoLibrary
        "hundred" -> Icons.Rounded.EmojiEvents
        "streak30" -> Icons.Rounded.LocalFireDepartment
        "categories10" -> Icons.Rounded.Category
        "big_saver" -> Icons.Rounded.AccountBalance
        "goal_three" -> Icons.Rounded.WorkspacePremium
        "challenge_five" -> Icons.Rounded.MilitaryTech
        "five_hundred" -> Icons.Rounded.Diamond
        "perfect_month" -> Icons.Rounded.EventAvailable
        "surplus_three" -> Icons.Rounded.Insights
        "photo100" -> Icons.Rounded.Collections
        "fortune" -> Icons.Rounded.Paid
        "streak100" -> Icons.Rounded.Whatshot
        "year_tracked" -> Icons.Rounded.CalendarMonth
        "goal_ten" -> Icons.Rounded.Stars
        "challenge_twenty" -> Icons.Rounded.Shield
        "thousand" -> Icons.Rounded.AutoAwesome
        else -> Icons.Rounded.Verified
    }

    fun titleRes(key: String): Int = when (key) {
        "first_steps" -> R.string.badge_first_steps
        "income_first" -> R.string.badge_income_first
        "ten" -> R.string.badge_ten
        "scanner" -> R.string.badge_scanner
        "saver" -> R.string.badge_saver
        "first_goal" -> R.string.badge_first_goal
        "streak7" -> R.string.badge_streak7
        "surplus" -> R.string.badge_surplus
        "goal_done" -> R.string.badge_goal_done
        "challenge_done" -> R.string.badge_challenge_done
        "night_owl" -> R.string.badge_night_owl
        "debt_closed" -> R.string.badge_debt_closed
        "photo10" -> R.string.badge_photo10
        "hundred" -> R.string.badge_hundred
        "streak30" -> R.string.badge_streak30
        "categories10" -> R.string.badge_categories10
        "big_saver" -> R.string.badge_big_saver
        "goal_three" -> R.string.badge_goal_three
        "challenge_five" -> R.string.badge_challenge_five
        "five_hundred" -> R.string.badge_five_hundred
        "perfect_month" -> R.string.badge_perfect_month
        "surplus_three" -> R.string.badge_surplus_three
        "photo100" -> R.string.badge_photo100
        "fortune" -> R.string.badge_fortune
        "streak100" -> R.string.badge_streak100
        "year_tracked" -> R.string.badge_year_tracked
        "goal_ten" -> R.string.badge_goal_ten
        "challenge_twenty" -> R.string.badge_challenge_twenty
        "thousand" -> R.string.badge_thousand
        else -> R.string.badge_streak365
    }

    fun descRes(key: String): Int = when (key) {
        "first_steps" -> R.string.badge_first_steps_desc
        "income_first" -> R.string.badge_income_first_desc
        "ten" -> R.string.badge_ten_desc
        "scanner" -> R.string.badge_scanner_desc
        "saver" -> R.string.badge_saver_desc
        "first_goal" -> R.string.badge_first_goal_desc
        "streak7" -> R.string.badge_streak7_desc
        "surplus" -> R.string.badge_surplus_desc
        "goal_done" -> R.string.badge_goal_done_desc
        "challenge_done" -> R.string.badge_challenge_done_desc
        "night_owl" -> R.string.badge_night_owl_desc
        "debt_closed" -> R.string.badge_debt_closed_desc
        "photo10" -> R.string.badge_photo10_desc
        "hundred" -> R.string.badge_hundred_desc
        "streak30" -> R.string.badge_streak30_desc
        "categories10" -> R.string.badge_categories10_desc
        "big_saver" -> R.string.badge_big_saver_desc
        "goal_three" -> R.string.badge_goal_three_desc
        "challenge_five" -> R.string.badge_challenge_five_desc
        "five_hundred" -> R.string.badge_five_hundred_desc
        "perfect_month" -> R.string.badge_perfect_month_desc
        "surplus_three" -> R.string.badge_surplus_three_desc
        "photo100" -> R.string.badge_photo100_desc
        "fortune" -> R.string.badge_fortune_desc
        "streak100" -> R.string.badge_streak100_desc
        "year_tracked" -> R.string.badge_year_tracked_desc
        "goal_ten" -> R.string.badge_goal_ten_desc
        "challenge_twenty" -> R.string.badge_challenge_twenty_desc
        "thousand" -> R.string.badge_thousand_desc
        else -> R.string.badge_streak365_desc
    }
}
