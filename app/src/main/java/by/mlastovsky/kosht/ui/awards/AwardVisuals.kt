package by.mlastovsky.kosht.ui.awards

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Beenhere
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CameraRoll
import androidx.compose.material.icons.rounded.Castle
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EmojiFlags
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.HourglassFull
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PriceCheck
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timeline
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
        "night100" -> Icons.Rounded.DarkMode
        "categories20" -> Icons.Rounded.Apps
        "photo500" -> Icons.Rounded.CameraRoll
        "debt_free" -> Icons.Rounded.Handshake
        "goal_25" -> Icons.Rounded.Grade
        "challenge_fifty" -> Icons.Rounded.FitnessCenter
        "surplus_year" -> Icons.Rounded.Timeline
        "perfect_year" -> Icons.Rounded.Beenhere
        "treasury" -> Icons.Rounded.AccountBalanceWallet
        "five_thousand" -> Icons.Rounded.RocketLaunch
        "three_years" -> Icons.Rounded.HourglassFull
        "millionaire" -> Icons.Rounded.Castle
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
        "night100" -> R.string.badge_night100
        "categories20" -> R.string.badge_categories20
        "photo500" -> R.string.badge_photo500
        "debt_free" -> R.string.badge_debt_free
        "goal_25" -> R.string.badge_goal_25
        "challenge_fifty" -> R.string.badge_challenge_fifty
        "surplus_year" -> R.string.badge_surplus_year
        "perfect_year" -> R.string.badge_perfect_year
        "treasury" -> R.string.badge_treasury
        "five_thousand" -> R.string.badge_five_thousand
        "three_years" -> R.string.badge_three_years
        "millionaire" -> R.string.badge_millionaire
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
        "night100" -> R.string.badge_night100_desc
        "categories20" -> R.string.badge_categories20_desc
        "photo500" -> R.string.badge_photo500_desc
        "debt_free" -> R.string.badge_debt_free_desc
        "goal_25" -> R.string.badge_goal_25_desc
        "challenge_fifty" -> R.string.badge_challenge_fifty_desc
        "surplus_year" -> R.string.badge_surplus_year_desc
        "perfect_year" -> R.string.badge_perfect_year_desc
        "treasury" -> R.string.badge_treasury_desc
        "five_thousand" -> R.string.badge_five_thousand_desc
        "three_years" -> R.string.badge_three_years_desc
        "millionaire" -> R.string.badge_millionaire_desc
        else -> R.string.badge_streak365_desc
    }
}
