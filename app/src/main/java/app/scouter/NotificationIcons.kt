package app.scouter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.drawable.Icon
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import app.scouter.model.ScouterDocument
import app.scouter.model.notificationIconName
import app.scouter.ui.iconVector
import kotlin.math.roundToInt

/**
 * Builds the icons for a document's pending notification.
 *
 * Material glyphs only exist as Compose [ImageVector]s, which a notification
 * cannot consume, so the vector is rasterized here. The small icon is a white
 * alpha mask for the status bar; the large icon is a tinted disc using the
 * document theme, which One UI and stock Android show in the notification row.
 * Both fall back to the Scouter glyph when the document carries no icon.
 */
internal object NotificationIcons {
    private const val SMALL_DP = 24f
    private const val LARGE_DP = 64f
    private const val RENDER_SCALE = 2
    /** Glyph footprint inside the large disc, as a fraction of its diameter. */
    private const val LARGE_GLYPH_FRACTION = .5f

    fun smallIcon(context: Context, document: ScouterDocument): Icon {
        val vector = document.notificationIconName()?.iconVector()
            ?: return Icon.createWithResource(context, R.drawable.ic_scouter_notification)
        return runCatching {
            val px = pixels(context, SMALL_DP)
            Icon.createWithBitmap(rasterize(vector, px, android.graphics.Color.WHITE, null))
        }.getOrElse { Icon.createWithResource(context, R.drawable.ic_scouter_notification) }
    }

    fun largeIcon(context: Context, document: ScouterDocument): Icon? {
        val vector = document.notificationIconName()?.iconVector() ?: return null
        return runCatching {
            val px = pixels(context, LARGE_DP)
            val glyph = parseColor(document.theme.accent, android.graphics.Color.WHITE)
            val disc = parseColor(document.theme.background, 0xFF07100D.toInt())
            Icon.createWithBitmap(rasterize(vector, px, glyph, disc))
        }.getOrNull()
    }

    private fun pixels(context: Context, dp: Float): Int =
        (dp * context.resources.displayMetrics.density * RENDER_SCALE).roundToInt()

    private fun parseColor(value: String, fallback: Int): Int =
        runCatching { android.graphics.Color.parseColor(value) }.getOrDefault(fallback)

    /**
     * Draws [vector] into a square bitmap of [px] pixels. With a [disc] color
     * the glyph is centered on a filled circle and shrunk to [LARGE_GLYPH_FRACTION];
     * otherwise it fills the whole bitmap as a plain mask.
     */
    private fun rasterize(vector: ImageVector, px: Int, glyphColor: Int, disc: Int?): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        var glyphPx = px.toFloat()
        if (disc != null) {
            paint.color = disc
            canvas.drawCircle(px / 2f, px / 2f, px / 2f, paint)
            glyphPx = px * LARGE_GLYPH_FRACTION
        }
        val inset = (px - glyphPx) / 2f
        canvas.translate(inset, inset)
        canvas.scale(glyphPx / vector.viewportWidth, glyphPx / vector.viewportHeight)
        paint.color = glyphColor
        drawGroup(canvas, vector.root, paint)
        return bitmap
    }

    private fun drawGroup(canvas: Canvas, group: VectorGroup, paint: Paint) {
        canvas.save()
        canvas.translate(group.translationX, group.translationY)
        canvas.translate(group.pivotX, group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        val baseAlpha = paint.alpha
        for (node in group) {
            when (node) {
                is VectorGroup -> drawGroup(canvas, node, paint)
                is VectorPath -> {
                    val path: AndroidPath = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                    path.fillType = if (node.pathFillType == PathFillType.EvenOdd) {
                        AndroidPath.FillType.EVEN_ODD
                    } else {
                        AndroidPath.FillType.WINDING
                    }
                    paint.alpha = (baseAlpha * node.fillAlpha).roundToInt().coerceIn(0, 255)
                    canvas.drawPath(path, paint)
                    paint.alpha = baseAlpha
                }
            }
        }
        canvas.restore()
    }
}
