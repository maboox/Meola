package ir.nama.launcher.ui.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Brightness3
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.ui.graphics.vector.ImageVector
import ir.nama.launcher.data.WidgetType
import ir.nama.launcher.tr

/** A widget size in grid cells. */
data class WSize(val w: Int, val h: Int) {
    override fun toString() = "$w×$h"
}

object WidgetCatalog {
    /** Supported sizes; the first one is the default. Sizes assume a 4-column grid. */
    fun sizes(t: WidgetType): List<WSize> = when (t) {
        WidgetType.GLANCE -> listOf(WSize(4, 1), WSize(4, 2))
        WidgetType.CLOCK -> listOf(WSize(2, 2), WSize(4, 2), WSize(2, 1))
        WidgetType.CALENDAR -> listOf(WSize(2, 2), WSize(4, 3), WSize(4, 4))
        WidgetType.TODAY -> listOf(WSize(4, 2), WSize(2, 2))
        WidgetType.PRAYER -> listOf(WSize(4, 1), WSize(4, 2), WSize(2, 2))
        WidgetType.COUNTDOWN -> listOf(WSize(2, 2), WSize(2, 1))
        WidgetType.BIRTHDAYS -> listOf(WSize(4, 2), WSize(2, 2))
        WidgetType.WEATHER -> listOf(WSize(2, 2), WSize(2, 1), WSize(4, 1), WSize(4, 2))
        WidgetType.BLACKOUT -> listOf(WSize(4, 1), WSize(2, 2), WSize(4, 2))
        WidgetType.ODD_EVEN -> listOf(WSize(2, 1), WSize(2, 2))
        WidgetType.FOOTBALL -> listOf(WSize(4, 2), WSize(4, 3))
        WidgetType.PRICES -> listOf(WSize(2, 2), WSize(4, 2), WSize(4, 1))
        WidgetType.BILLS -> listOf(WSize(4, 2), WSize(2, 2))
        WidgetType.BANK_CARDS -> listOf(WSize(4, 2), WSize(4, 3))
        WidgetType.EXPENSES -> listOf(WSize(2, 2), WSize(2, 1))
        WidgetType.TODO -> listOf(WSize(2, 2), WSize(4, 2), WSize(4, 3))
        WidgetType.SHOPPING -> listOf(WSize(2, 2), WSize(4, 2), WSize(4, 3))
        WidgetType.NOTES -> listOf(WSize(2, 2), WSize(4, 2))
        WidgetType.HABITS -> listOf(WSize(4, 2), WSize(2, 2))
        WidgetType.POMODORO -> listOf(WSize(2, 2), WSize(2, 1))
        WidgetType.BATTERY -> listOf(WSize(2, 1), WSize(2, 2))
        WidgetType.DATA_USAGE -> listOf(WSize(2, 2), WSize(4, 1))
        WidgetType.USSD -> listOf(WSize(4, 2), WSize(4, 1))
        WidgetType.TOGGLES -> listOf(WSize(4, 1))
        WidgetType.MUSIC -> listOf(WSize(4, 1))
        WidgetType.CONTACTS -> listOf(WSize(4, 1), WSize(4, 2))
        WidgetType.HAFEZ -> listOf(WSize(4, 2))
        WidgetType.POEM -> listOf(WSize(4, 2))
        WidgetType.QUOTE -> listOf(WSize(4, 1), WSize(4, 2))
        WidgetType.VERSE -> listOf(WSize(4, 2))
        WidgetType.NEWS -> listOf(WSize(4, 2), WSize(4, 1), WSize(4, 3))
        WidgetType.NOTIF_DIGEST -> listOf(WSize(4, 2))
        WidgetType.USAGE -> listOf(WSize(2, 2), WSize(4, 2))
        WidgetType.FOCUS -> listOf(WSize(2, 1), WSize(2, 2))
        WidgetType.SPACE -> listOf(WSize(2, 1))
        WidgetType.SMART_STACK -> listOf(WSize(4, 2))
        WidgetType.SYSTEM -> listOf(WSize(4, 2))
    }

    fun defaultSize(t: WidgetType) = sizes(t).first()

    fun icon(t: WidgetType): ImageVector = when (t) {
        WidgetType.GLANCE -> Icons.Outlined.WbSunny
        WidgetType.CLOCK -> Icons.Outlined.AccessTime
        WidgetType.CALENDAR -> Icons.Outlined.CalendarToday
        WidgetType.TODAY -> Icons.Outlined.CheckCircle
        WidgetType.PRAYER -> Icons.Outlined.Brightness3
        WidgetType.COUNTDOWN -> Icons.Outlined.HourglassTop
        WidgetType.BIRTHDAYS -> Icons.Outlined.Cake
        WidgetType.WEATHER -> Icons.Outlined.WbSunny
        WidgetType.BLACKOUT -> Icons.Outlined.Bolt
        WidgetType.ODD_EVEN -> Icons.Outlined.DirectionsCar
        WidgetType.FOOTBALL -> Icons.Outlined.SportsSoccer
        WidgetType.PRICES -> Icons.Outlined.TrendingUp
        WidgetType.BILLS -> Icons.Outlined.ReceiptLong
        WidgetType.BANK_CARDS -> Icons.Outlined.CreditCard
        WidgetType.EXPENSES -> Icons.Outlined.Payments
        WidgetType.TODO -> Icons.Outlined.CheckCircle
        WidgetType.SHOPPING -> Icons.Outlined.ShoppingCart
        WidgetType.NOTES -> Icons.Outlined.StickyNote2
        WidgetType.HABITS -> Icons.Outlined.DonutLarge
        WidgetType.POMODORO -> Icons.Outlined.Timer
        WidgetType.BATTERY -> Icons.Outlined.BatteryChargingFull
        WidgetType.DATA_USAGE -> Icons.Outlined.DataUsage
        WidgetType.USSD -> Icons.Outlined.SimCard
        WidgetType.TOGGLES -> Icons.Outlined.ToggleOn
        WidgetType.MUSIC -> Icons.Outlined.MusicNote
        WidgetType.CONTACTS -> Icons.Outlined.Contacts
        WidgetType.HAFEZ, WidgetType.POEM -> Icons.Outlined.MenuBook
        WidgetType.QUOTE, WidgetType.VERSE -> Icons.Outlined.FormatQuote
        WidgetType.NEWS -> Icons.Outlined.Article
        WidgetType.NOTIF_DIGEST -> Icons.Outlined.Notifications
        WidgetType.USAGE -> Icons.Outlined.HourglassEmpty
        WidgetType.FOCUS -> Icons.Outlined.CenterFocusStrong
        WidgetType.SPACE -> Icons.Outlined.Dashboard
        WidgetType.SMART_STACK -> Icons.Outlined.Layers
        WidgetType.SYSTEM -> Icons.Outlined.Widgets
    }

    fun description(t: WidgetType): String = when (t) {
        WidgetType.GLANCE -> tr("تاریخ، هوا و رویداد بعدی، بدون کادر", "Date, weather and what's next")
        WidgetType.CLOCK -> tr("ساعت عقربه‌ای یا دیجیتال", "Analog or digital clock")
        WidgetType.CALENDAR -> tr("تقویم شمسی با تعطیلی‌ها و تاریخ قمری", "Jalali calendar with holidays")
        WidgetType.TODAY -> tr("آلارم، قسط و کارهای امروز در یک کارت", "Alarm, bills and tasks for today")
        WidgetType.PRAYER -> tr("اذان بعدی و اوقات امروز", "Next prayer and today's times")
        WidgetType.COUNTDOWN -> tr("روزهای مانده تا نوروز یا هر تاریخی", "Days until Nowruz or any date")
        WidgetType.BIRTHDAYS -> tr("تولدهای نزدیک از مخاطبین", "Upcoming birthdays from contacts")
        WidgetType.WEATHER -> tr("دما و آلودگی هوا", "Temperature and air quality")
        WidgetType.BLACKOUT -> tr("زمان خاموشی منطقه شما", "Your area's power cuts")
        WidgetType.ODD_EVEN -> tr("امروز با این پلاک مجازی یا نه", "Can you drive today")
        WidgetType.FOOTBALL -> tr("خبرهای تیم محبوبت", "News about your team")
        WidgetType.PRICES -> tr("دلار، بیت‌کوین و اتریوم", "Dollar, bitcoin, ether")
        WidgetType.BILLS -> tr("سررسید قسط‌ها و قبض‌ها", "Bill due dates")
        WidgetType.BANK_CARDS -> tr("شماره کارت و شبا با یک لمس", "Card and IBAN in one tap")
        WidgetType.EXPENSES -> tr("خرج امروز و این ماه", "Spent today and this month")
        WidgetType.TODO -> tr("کارهای روز", "Today's tasks")
        WidgetType.SHOPPING -> tr("لیست خرید", "Shopping list")
        WidgetType.NOTES -> tr("یادداشت سریع", "Quick note")
        WidgetType.HABITS -> tr("عادت‌های روزانه و زنجیره", "Daily habits and streaks")
        WidgetType.POMODORO -> tr("تایمر تمرکز ۲۵ دقیقه‌ای", "25-minute focus timer")
        WidgetType.BATTERY -> tr("شارژ گوشی", "Battery level")
        WidgetType.DATA_USAGE -> tr("مصرف اینترنت و بسته", "Data used and package")
        WidgetType.USSD -> tr("موجودی و خرید بسته با یک لمس", "Balance and packages in one tap")
        WidgetType.TOGGLES -> tr("چراغ‌قوه، وای‌فای، صدا…", "Torch, Wi-Fi, volume…")
        WidgetType.MUSIC -> tr("قبلی، پخش و بعدی", "Previous, play, next")
        WidgetType.CONTACTS -> tr("تماس سریع با عزیزان", "Quick call")
        WidgetType.HAFEZ -> tr("یک بیت از حافظ برای امروز", "A Hafez couplet for today")
        WidgetType.POEM -> tr("شعر روز از شاعران فارسی", "A Persian poem for today")
        WidgetType.QUOTE -> tr("یک جمله کوتاه برای امروز", "A short line for today")
        WidgetType.VERSE -> tr("آیه روز با ترجمه", "Verse of the day")
        WidgetType.NEWS -> tr("تیتر خبرها از منبع‌های شما", "Headlines from your sources")
        WidgetType.NOTIF_DIGEST -> tr("خلاصه اعلان‌های مهم", "Important notifications")
        WidgetType.USAGE -> tr("امروز چقدر با گوشی بودی", "Time on your phone today")
        WidgetType.FOCUS -> tr("شروع حالت تمرکز", "Start focus mode")
        WidgetType.SPACE -> tr("فضای فعال و تغییر آن", "Active space")
        WidgetType.SMART_STACK -> tr("ویجت مناسب هر ساعت روز", "The right widget for the time of day")
        WidgetType.SYSTEM -> ""
    }

    val GROUPS = listOf(
        "time" to ("زمان و تقویم" to "Time"),
        "city" to ("شهر و هوا" to "City"),
        "money" to ("مالی" to "Money"),
        "productivity" to ("کارها" to "Productivity"),
        "phone" to ("گوشی" to "Phone"),
        "culture" to ("شعر و فرهنگ" to "Culture"),
        "news" to ("خبر و اعلان" to "News"),
        "wellbeing" to ("تمرکز" to "Focus")
    )
}
