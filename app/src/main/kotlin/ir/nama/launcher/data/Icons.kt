package ir.nama.launcher.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object IconShapes {
    /** Path for a shape inside a square of size [s]. */
    fun path(shape: IconShape, s: Float): Path {
        val p = Path()
        when (shape) {
            IconShape.CIRCLE -> p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
            IconShape.ROUNDED -> p.addRoundRect(RectF(0f, 0f, s, s), s * 0.22f, s * 0.22f, Path.Direction.CW)
            IconShape.SQUIRCLE -> {
                // Superellipse approximation.
                val r = s / 2
                val c = r * 0.92f
                p.moveTo(0f, r)
                p.cubicTo(0f, r - c, r - c, 0f, r, 0f)
                p.cubicTo(r + c, 0f, s, r - c, s, r)
                p.cubicTo(s, r + c, r + c, s, r, s)
                p.cubicTo(r - c, s, 0f, r + c, 0f, r)
                p.close()
            }
            IconShape.ARCH -> {
                // Persian arch: pointed top, straight sides, slightly rounded base.
                val br = s * 0.16f
                p.moveTo(0f, s * 0.42f)
                p.cubicTo(0f, s * 0.16f, s * 0.30f, s * 0.04f, s / 2, 0f)
                p.cubicTo(s * 0.70f, s * 0.04f, s, s * 0.16f, s, s * 0.42f)
                p.lineTo(s, s - br)
                p.quadTo(s, s, s - br, s)
                p.lineTo(br, s)
                p.quadTo(0f, s, 0f, s - br)
                p.close()
            }
            IconShape.TEARDROP -> {
                val r = s * 0.24f
                p.addRoundRect(RectF(0f, 0f, s, s), floatArrayOf(s / 2, s / 2, s / 2, s / 2, r, r, s / 2, s / 2), Path.Direction.CW)
            }
            IconShape.ORIGINAL -> p.addRect(0f, 0f, s, s, Path.Direction.CW)
        }
        return p
    }
}

/** Reads ADW-style icon packs (appfilter.xml). */
class IconPackManager(private val context: Context) {
    data class Pack(val packageName: String, val label: String)

    private var loadedPack: String? = null
    private var map: Map<String, String> = emptyMap()
    private var res: Resources? = null

    fun available(): List<Pack> = try {
        val pm = context.packageManager
        val intents = listOf(Intent("org.adw.launcher.THEMES"), Intent("com.novalauncher.THEME"))
        intents.flatMap { pm.queryIntentActivities(it, PackageManager.GET_META_DATA) }
            .map { Pack(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    private fun ensure(pkg: String) {
        if (loadedPack == pkg) return
        loadedPack = pkg
        map = emptyMap()
        res = null
        try {
            val r = context.packageManager.getResourcesForApplication(pkg)
            res = r
            val parser: XmlPullParser = run {
                @SuppressLint("DiscouragedApi")
                val id = r.getIdentifier("appfilter", "xml", pkg)
                if (id != 0) r.getXml(id) else {
                    val f = XmlPullParserFactory.newInstance().newPullParser()
                    f.setInput(r.assets.open("appfilter.xml"), "UTF-8")
                    f
                }
            }
            val m = HashMap<String, String>()
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val draw = parser.getAttributeValue(null, "drawable")
                    if (comp != null && draw != null && comp.startsWith("ComponentInfo{")) {
                        m[comp.removePrefix("ComponentInfo{").removeSuffix("}")] = draw
                    }
                }
                ev = parser.next()
            }
            map = m
        } catch (e: Exception) {
            map = emptyMap()
        }
    }

    @SuppressLint("DiscouragedApi", "UseCompatLoadingForDrawables")
    fun drawable(pkg: String, component: String, density: Int): Drawable? {
        ensure(pkg)
        val name = map[component] ?: return null
        val r = res ?: return null
        return try {
            val id = r.getIdentifier(name, "drawable", pkg)
            if (id == 0) null else r.getDrawableForDensity(id, density, null)
        } catch (e: Exception) {
            null
        }
    }
}

/** How icons are drawn: as the app made them, tinted from the wallpaper, or single colour (minimal). */
data class IconMode(val kind: Kind, val fg: Int = 0, val bg: Int = 0) {
    enum class Kind { NORMAL, THEMED, MONO }
    companion object { val NORMAL = IconMode(Kind.NORMAL) }
}

class IconLoader(private val context: Context, private val apps: AppsRepository, val packs: IconPackManager) {
    private val cache = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(3)

    private fun cacheKey(key: String, shape: IconShape, px: Int, pack: String?, mode: IconMode) = "$key|$shape|$px|$pack|$mode"

    fun peek(key: String, shape: IconShape, px: Int, pack: String?, mode: IconMode = IconMode.NORMAL): ImageBitmap? =
        cache.get(cacheKey(key, shape, px, pack, mode))

    fun clear() = cache.evictAll()

    suspend fun load(key: String, shape: IconShape, px: Int, pack: String?, mode: IconMode = IconMode.NORMAL): ImageBitmap? {
        val ck = cacheKey(key, shape, px, pack, mode)
        cache.get(ck)?.let { return it }
        return withContext(io) {
            cache.get(ck) ?: render(key, shape, px, pack, mode)?.also { cache.put(ck, it) }
        }
    }

    private fun render(key: String, shape: IconShape, px: Int, pack: String?, mode: IconMode): ImageBitmap? {
        val info = apps.info(key) ?: return null
        val density = context.resources.displayMetrics.densityDpi
        val packDrawable = pack?.let {
            packs.drawable(it, "${info.componentName.packageName}/${info.componentName.className}", density)
        }
        val d = packDrawable ?: try {
            info.getBadgedIcon(density)
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            when (mode.kind) {
                IconMode.Kind.NORMAL -> drawableToBitmap(d, shape, px, isPackIcon = packDrawable != null)
                IconMode.Kind.THEMED -> themedBitmap(d, shape, px, mode) ?: drawableToBitmap(d, shape, px, isPackIcon = packDrawable != null)
                IconMode.Kind.MONO -> monoBitmap(d, px, mode)
            }.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private fun monochromeLayer(d: Drawable): Drawable? =
            if (android.os.Build.VERSION.SDK_INT >= 33 && d is AdaptiveIconDrawable) d.monochrome else null

        /** Android 13 themed icon: the app's monochrome layer tinted, on a tonal background. */
        fun themedBitmap(d: Drawable, shape: IconShape, px: Int, mode: IconMode): Bitmap? {
            val mono = monochromeLayer(d) ?: return null
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val s = px.toFloat()
            c.save()
            c.clipPath(IconShapes.path(if (shape == IconShape.ORIGINAL) IconShape.CIRCLE else shape, s))
            c.drawColor(mode.bg)
            val inset = (px / 4f).toInt()
            mono.mutate().setTint(mode.fg)
            mono.setBounds(-inset, -inset, px + inset, px + inset)
            mono.draw(c)
            c.restore()
            return bmp
        }

        /** Minimal style: monochrome layer in one colour, or a desaturated original icon. */
        fun monoBitmap(d: Drawable, px: Int, mode: IconMode): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val mono = monochromeLayer(d)
            if (mono != null) {
                val inset = (px / 4f).toInt()
                mono.mutate().setTint(mode.fg)
                mono.setBounds(-inset, -inset, px + inset, px + inset)
                mono.draw(c)
            } else {
                val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                val copy = d.constantState?.newDrawable()?.mutate() ?: d.mutate()
                copy.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
                if (copy is AdaptiveIconDrawable) {
                    c.save()
                    c.clipPath(IconShapes.path(IconShape.CIRCLE, px.toFloat()))
                    val inset = (px / 4f).toInt()
                    copy.background?.let { it.colorFilter = android.graphics.ColorMatrixColorFilter(cm); it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(c) }
                    copy.foreground?.let { it.colorFilter = android.graphics.ColorMatrixColorFilter(cm); it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(c) }
                    c.restore()
                } else {
                    copy.setBounds(0, 0, px, px)
                    copy.draw(c)
                }
            }
            return bmp
        }

        fun drawableToBitmap(d: Drawable, shape: IconShape, px: Int, isPackIcon: Boolean = false): Bitmap {
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val s = px.toFloat()
            if (d is AdaptiveIconDrawable && shape != IconShape.ORIGINAL && !isPackIcon) {
                c.save()
                c.clipPath(IconShapes.path(shape, s))
                // Adaptive layers are 108dp with a 72dp safe zone: draw them 50% larger than the canvas.
                val inset = (px / 4f).toInt()
                d.background?.let { it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(c) }
                d.foreground?.let { it.setBounds(-inset, -inset, px + inset, px + inset); it.draw(c) }
                c.restore()
            } else {
                d.setBounds(0, 0, px, px)
                d.draw(c)
            }
            return bmp
        }
    }
}
