package ir.nama.launcher.context

import ir.nama.core.AppCategory
import ir.nama.core.ContextSnapshot
import ir.nama.core.Space
import ir.nama.core.SpaceEngine
import ir.nama.core.SpaceOverrides
import ir.nama.core.StyleId
import ir.nama.core.Trigger
import ir.nama.launcher.data.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

class SpaceManager(private val store: Store, snapshot: StateFlow<ContextSnapshot>, scope: CoroutineScope) {

    val active: StateFlow<Space?> = combine(
        snapshot,
        store.spaces.flow,
        store.settings.flow.map { it.manualSpaceId }.distinctUntilChanged()
    ) { ctx, spaces, manual ->
        SpaceEngine.resolve(spaces, ctx, manual)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    fun setManual(id: String?) = store.settings.update { it.copy(manualSpaceId = id) }

    fun upsert(space: Space) = store.spaces.update { list ->
        if (list.any { it.id == space.id }) list.map { if (it.id == space.id) space else it } else list + space
    }

    fun delete(id: String) {
        store.spaces.update { list -> list.filterNot { it.id == id } }
        if (store.settings.value.manualSpaceId == id) setManual(null)
    }

    companion object {
        private fun id() = "s_" + UUID.randomUUID().toString().take(8)

        private val WEEKDAYS = setOf(0, 1, 2, 3, 4) // Saturday..Wednesday

        data class Preset(val key: String, val fa: String, val en: String, val icon: String, val descFa: String, val build: () -> Space)

        val PRESETS: List<Preset> = listOf(
            Preset("work", "شرکت", "Work", "💼", "وقتی به وای‌فای شرکت وصل می‌شوی یا در ساعت کاری") {
                Space(
                    id(), "شرکت", "💼", priority = 30, matchAll = false,
                    triggers = listOf(Trigger.Time(8 * 60, 17 * 60, WEEKDAYS)),
                    overrides = SpaceOverrides(style = StyleId.DASHBOARD, hiddenCategories = setOf(AppCategory.GAMES)),
                    preset = "work"
                )
            },
            Preset("home", "خانه", "Home", "🏠", "وقتی به وای‌فای خانه وصل هستی") {
                Space(
                    id(), "خانه", "🏠", priority = 40,
                    triggers = listOf(Trigger.Wifi(emptyList())),
                    overrides = SpaceOverrides(),
                    preset = "home"
                )
            },
            Preset("sleep", "خواب", "Sleep", "🌙", "شب‌ها: صفحه ساده و تیره، شبکه‌های اجتماعی پنهان") {
                Space(
                    id(), "خواب", "🌙", priority = 10,
                    triggers = listOf(Trigger.Time(23 * 60, 6 * 60 + 30, (0..6).toSet())),
                    overrides = SpaceOverrides(
                        style = StyleId.MINIMAL,
                        hiddenCategories = setOf(AppCategory.SOCIAL, AppCategory.GAMES),
                        reduceMotion = true, calmNews = true, darkTint = true
                    ),
                    preset = "sleep"
                )
            },
            Preset("car", "ماشین", "Car", "🚗", "وقتی بلوتوث ماشین وصل می‌شود") {
                Space(
                    id(), "ماشین", "🚗", priority = 15,
                    triggers = listOf(Trigger.Bluetooth(emptyList())),
                    overrides = SpaceOverrides(style = StyleId.MINIMAL, hideNews = true),
                    preset = "car"
                )
            },
            Preset("university", "دانشگاه", "University", "🎓", "در محدوده دانشگاه یا وای‌فای آن") {
                Space(
                    id(), "دانشگاه", "🎓", priority = 35, matchAll = false,
                    triggers = listOf(Trigger.Wifi(emptyList())),
                    overrides = SpaceOverrides(hiddenCategories = setOf(AppCategory.GAMES)),
                    preset = "university"
                )
            },
            Preset("gym", "باشگاه", "Gym", "🏋", "وقتی هدفون وصل است و در باشگاه هستی") {
                Space(
                    id(), "باشگاه", "🏋", priority = 45,
                    triggers = listOf(Trigger.Headphones),
                    overrides = SpaceOverrides(style = StyleId.MINIMAL),
                    preset = "gym"
                )
            },
            Preset("battery", "صرفه‌جویی باتری", "Battery saver", "🔋", "وقتی باتری زیر ۲۰٪ است: مینیمال و بدون انیمیشن") {
                Space(
                    id(), "صرفه‌جویی باتری", "🔋", priority = 5,
                    triggers = listOf(Trigger.BatteryBelow(20)),
                    overrides = SpaceOverrides(style = StyleId.MINIMAL, reduceMotion = true, darkTint = true),
                    preset = "battery"
                )
            },
            Preset("ramadan", "ماه رمضان", "Ramadan", "🌙", "در ماه رمضان: اوقات شرعی و سحر و افطار جلوتر") {
                Space(
                    id(), "ماه رمضان", "☪", priority = 60,
                    triggers = listOf(Trigger.Ramadan),
                    overrides = SpaceOverrides(style = StyleId.PERSIAN),
                    preset = "ramadan"
                )
            },
            Preset("holiday", "تعطیلی", "Holiday", "🌴", "جمعه‌ها و تعطیلات رسمی") {
                Space(
                    id(), "تعطیلی", "🌴", priority = 55,
                    triggers = listOf(Trigger.Holiday),
                    overrides = SpaceOverrides(hiddenCategories = setOf(AppCategory.PRODUCTIVITY)),
                    preset = "holiday"
                )
            },
            Preset("national", "اینترنت ملی", "National internet", "🌐", "وقتی اینترنت بین‌الملل قطع است، برنامه‌های خارجی پنهان می‌شوند") {
                Space(
                    id(), "اینترنت ملی", "🌐", priority = 8,
                    triggers = listOf(Trigger.NoInternationalInternet),
                    overrides = SpaceOverrides(hideForeignApps = true),
                    preset = "national"
                )
            },
            Preset("kids", "کودک", "Kids", "🧸", "فقط برنامه‌های مجاز؛ خروج با قفل گوشی") {
                Space(
                    id(), "کودک", "🧸", priority = 0,
                    triggers = emptyList(),
                    overrides = SpaceOverrides(
                        style = StyleId.DASHBOARD, locked = true,
                        allowOnlyCategories = setOf(AppCategory.GAMES, AppCategory.EDUCATION),
                        hideNews = true
                    ),
                    preset = "kids"
                )
            },
            Preset("guest", "مهمان", "Guest", "👤", "پیام‌رسان‌ها، گالری و بانک پنهان؛ خروج با قفل گوشی") {
                Space(
                    id(), "مهمان", "👤", priority = 1,
                    triggers = emptyList(),
                    overrides = SpaceOverrides(
                        locked = true,
                        hiddenCategories = setOf(AppCategory.MESSAGING, AppCategory.SOCIAL, AppCategory.FINANCE, AppCategory.PHOTO),
                        hideNews = true
                    ),
                    preset = "guest"
                )
            },
            Preset("travel", "سفر", "Travel", "🧳", "دستی: نقشه، هتل و ترجمه جلوتر") {
                Space(
                    id(), "سفر", "🧳", priority = 25,
                    triggers = emptyList(),
                    overrides = SpaceOverrides(style = StyleId.GLASS),
                    preset = "travel"
                )
            }
        )
    }
}
