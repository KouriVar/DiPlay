// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.caverock.androidsvg.SVG
import com.shilapi.xcertplay.host.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Native, density-independent layout of the 1920 × 1080 Figma home screen. */
@Suppress("DEPRECATION")
internal class DeepalHomeView(
    context: Context,
    onHome: () -> Unit,
    onChoosePhone: () -> Unit,
    onConnect: () -> Unit,
    onSettings: () -> Unit,
) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private data class Slot(val view: View, val x: Float, val y: Float, val w: Float, val h: Float)
    private val slots = mutableListOf<Slot>()
    private val glass = mutableListOf<GlassDrawable>()
    private val handler = Handler(Looper.getMainLooper())
    private val font = ResourcesCompat.getFont(context, R.font.deepal_misans) ?: Typeface.DEFAULT
    private val poster = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setImageBitmap(context.assets.open("deepal-home/background-poster.jpg").use(BitmapFactory::decodeStream))
    }
    private val video = TextureView(context).apply {
        isOpaque = false
        surfaceTextureListener = this@DeepalHomeView
    }
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var resumed = false
    private var released = false
    private var prepared = false
    private var videoWidth = 1920
    private var videoHeight = 1080
    private var layoutScale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    private var renderScript: RenderScript? = null
    private var blurScript: ScriptIntrinsicBlur? = null
    private var inputAllocation: Allocation? = null
    private var outputAllocation: Allocation? = null
    private var captureBitmap: Bitmap? = null
    private var blurredBitmap: Bitmap? = null
    private val deviceName: TextView
    private val deviceStatus: TextView
    private val connectionHint: TextView
    private val connectionDetail: TextView
    private val connectionCard: FrameLayout
    private val enter: Button
    private val error: TextView

    init {
        setBackgroundColor(Color.rgb(28, 33, 43))
        addView(poster)
        addView(video)
        place(text("欢迎使用深蓝 CarPlay 系统", 32f, 500), 600f, 260f, 720f, 44f)

        val home = panel(32f, 102).apply { makeAction("车机主页", onHome) }
        place(home, 310f, 409f, 306f, 284f)
        child(home, icon("home"), 117f, 50f, 72f, 72f)
        child(home, text("车机主页", 24f, 600), 0f, 159f, 306f, 32f)
        child(home, text("返回深蓝车机主屏", 18f, 600), 0f, 199f, 306f, 25f)

        val middle = panel(32f, 102)
        place(middle, 643f, 384f, 634f, 334f)
        val choose = FrameLayout(context).apply { makeAction("选择设备", onChoosePhone) }
        child(middle, choose, 0f, 0f, 317f, 334f)
        child(choose, icon("car"), 122.5f, 74f, 72f, 72f)
        child(choose, text("选择设备", 24f, 600), 0f, 178f, 317f, 32f)
        deviceName = text("请选择 iPhone", 20f, 400)
        child(choose, deviceName, 12f, 222f, 293f, 27f)
        deviceStatus = text("等待选择设备", 16f, 400, Color.rgb(204, 204, 204))
        child(choose, deviceStatus, 12f, 261f, 293f, 23f)

        connectionCard = FrameLayout(context).apply { makeAction("连接手机", onConnect) }
        child(middle, connectionCard, 317f, 0f, 317f, 334f)
        child(connectionCard, icon("phone"), 122.5f, 74f, 72f, 72f)
        child(connectionCard, text("连接手机", 24f, 600), 0f, 178f, 317f, 32f)
        connectionHint = text("打开车机热点", 20f, 400)
        child(connectionCard, connectionHint, 12f, 222f, 293f, 27f)
        connectionDetail = text("保持双端 Wi-Fi、蓝牙开启", 16f, 400, Color.rgb(204, 204, 204))
        child(connectionCard, connectionDetail, 8f, 261f, 301f, 23f)
        child(middle, View(context).apply { setBackgroundColor(0x33C7C9CA) }, 316.5f, 1f, 1f, 332f)

        val settings = panel(32f, 102).apply { makeAction("软件设置", onSettings) }
        place(settings, 1304f, 409f, 306f, 284f)
        child(settings, icon("settings"), 117f, 50f, 72f, 72f)
        child(settings, text("软件设置", 24f, 600), 0f, 159f, 306f, 32f)
        child(settings, text("调整深蓝 CarPlay 设置", 18f, 600), 0f, 199f, 306f, 25f)

        val badge = panel(10f, 51)
        place(badge, 907.5f, 368f, 106f, 30f)
        child(badge, icon("flash"), 7f, 4f, 22f, 22f)
        child(badge, text("DeepalOS", 15f, 500), 29f, 4f, 70f, 22f)
        // This chip sits over the middle glass card in Figma (two 20% white layers).
        val or = panel(24f, 51, 92)
        place(or, 936f, 526f, 48f, 48f)
        child(or, text("or", 15f, 500).apply {
            typeface = ResourcesCompat.getFont(context, R.font.deepal_timeless)
            fontVariationSettings = "'wght' 500, 'ital' 0, 'STYL' 100"
        }, 0f, 0f, 48f, 48f)

        enter = Button(context).apply {
            text = "进入 CarPlay"
            typeface = font
            fontVariationSettings = "'wght' 450"
            setTextColor(Color.WHITE)
            isAllCaps = false
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minHeight = 0
            minimumWidth = 0
            minimumHeight = 0
            stateListAnimator = null
            background = GlassDrawable(24f, 51).also(glass::add)
            setOnClickListener { onConnect() }
        }
        place(enter, 807f, 834f, 306f, 64f)
        error = text("", 18f, 400, Color.rgb(255, 205, 149)).apply { visibility = GONE }
        place(error, 400f, 921f, 1120f, 60f)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun updateState(phoneName: String?, state: String, canConnect: Boolean, errorText: String?, sameLan: Boolean) {
        deviceName.text = phoneName ?: "请选择 iPhone"
        deviceStatus.text = state
        connectionHint.text = if (sameLan) "连接同一 Wi-Fi" else "打开车机热点"
        connectionCard.isEnabled = canConnect
        enter.isEnabled = canConnect
        connectionCard.alpha = if (canConnect) 1f else .5f
        enter.alpha = if (canConnect) 1f else .5f
        error.text = errorText ?: ""
        error.visibility = if (errorText == null) GONE else VISIBLE
        connectionCard.contentDescription = "连接手机，${connectionHint.text}"
    }

    private fun text(value: String, size: Float, weight: Int, color: Int = Color.WHITE) = TextView(context).apply {
        text = value
        tag = size
        typeface = font
        // MiSans uses its own axis coordinates: Regular 330, Medium 380, Demibold 450.
        fontVariationSettings = "'wght' ${when (weight) { 600 -> 450; 500 -> 380; else -> 330 }}"
        setTextColor(color)
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun icon(name: String) = ImageView(context).apply {
        // Keep the original Figma SVGs intact; render them at their authored dimensions.
        val picture = SVG.getFromAsset(context.assets, "deepal-home/$name.svg").renderToPicture()
        setImageDrawable(PictureDrawable(picture))
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun panel(radius: Float, borderAlpha: Int, tintAlpha: Int = 51) = FrameLayout(context).apply {
        background = GlassDrawable(radius, borderAlpha, tintAlpha).also(glass::add)
    }

    private fun View.makeAction(label: String, action: () -> Unit) {
        contentDescription = label
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setOnClickListener { action() }
    }

    private fun place(view: View, x: Float, y: Float, w: Float, h: Float) {
        slots.add(Slot(view, x, y, w, h))
        addView(view)
    }

    private fun child(parent: FrameLayout, view: View, x: Float, y: Float, w: Float, h: Float) {
        slots.add(Slot(view, x, y, w, h))
        parent.addView(view)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        layoutScale = min(w / 1920f, h / 1080f)
        offsetX = (w - 1920f * layoutScale) / 2f
        offsetY = (h - 1080f * layoutScale) / 2f
        poster.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        video.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        slots.forEach { slot ->
            if (slot.view is TextView) {
                val authoredSize = if (slot.view === enter) 24f else slot.view.tag as? Float ?: 24f
                slot.view.setTextSize(TypedValue.COMPLEX_UNIT_PX, authoredSize * layoutScale)
            }
            slot.view.measure(
                MeasureSpec.makeMeasureSpec((slot.w * layoutScale).roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((slot.h * layoutScale).roundToInt(), MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        poster.layout(0, 0, width, height)
        video.layout(0, 0, width, height)
        slots.forEach { slot ->
            val rootChild = slot.view.parent === this
            val x = ((if (rootChild) offsetX else 0f) + slot.x * layoutScale).roundToInt()
            val y = ((if (rootChild) offsetY else 0f) + slot.y * layoutScale).roundToInt()
            slot.view.layout(x, y, x + slot.view.measuredWidth, y + slot.view.measuredHeight)
        }
        updateVideoTransform()
    }

    fun resumeBackground() {
        if (released) return
        resumed = true
        if (video.isAvailable && player == null) prepareVideo()
        else if (prepared) { player?.start(); handler.post(blurTick) }
    }

    fun pauseBackground() {
        resumed = false
        handler.removeCallbacks(blurTick)
        disposePlayer()
    }

    fun releaseBackground() {
        if (released) return
        pauseBackground()
        released = true
        inputAllocation?.destroy(); inputAllocation = null
        outputAllocation?.destroy(); outputAllocation = null
        blurScript?.destroy(); blurScript = null
        renderScript?.destroy(); renderScript = null
        glass.clear()
        captureBitmap?.recycle(); captureBitmap = null
        blurredBitmap?.recycle(); blurredBitmap = null
    }

    private fun prepareVideo() {
        val texture = video.surfaceTexture ?: return
        if (!resumed || released) return
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        surface = Surface(texture)
        mediaPlayer.setSurface(surface)
        mediaPlayer.isLooping = true
        mediaPlayer.setVolume(0f, 0f)
        mediaPlayer.setOnPreparedListener {
            if (player !== it || !resumed || released) return@setOnPreparedListener
            videoWidth = it.videoWidth
            videoHeight = it.videoHeight
            updateVideoTransform()
            prepared = true
            it.start()
            handler.post(blurTick)
        }
        mediaPlayer.setOnErrorListener { _, _, _ -> disposePlayer(); true }
        runCatching {
            context.assets.openFd("deepal-home/background.mp4").use {
                mediaPlayer.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            mediaPlayer.prepareAsync()
        }.onFailure { disposePlayer() }
    }

    private fun disposePlayer() {
        prepared = false
        player?.release(); player = null
        surface?.release(); surface = null
    }

    private fun updateVideoTransform() {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val fit = max(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        video.setTransform(Matrix().apply {
            setScale(videoWidth * fit / width, videoHeight * fit / height, width / 2f, height / 2f)
        })
    }

    // One small blurred snapshot is shared by every glass panel, at 6 fps.
    // The video itself remains hardware decoded at its original frame rate.
    private val blurTick = object : Runnable {
        override fun run() {
            if (!resumed || !prepared || released || !video.isAvailable) return
            runCatching {
                if (captureBitmap == null) {
                    captureBitmap = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
                    blurredBitmap = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888)
                    val rs = RenderScript.create(context.applicationContext)
                    renderScript = rs
                    blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
                    inputAllocation = Allocation.createFromBitmap(rs, captureBitmap)
                    outputAllocation = Allocation.createFromBitmap(rs, blurredBitmap)
                }
                video.getBitmap(captureBitmap!!)
                inputAllocation!!.copyFrom(captureBitmap)
                blurScript!!.setRadius(6f)
                blurScript!!.setInput(inputAllocation)
                blurScript!!.forEach(outputAllocation)
                outputAllocation!!.copyTo(blurredBitmap)
                glass.forEach(Drawable::invalidateSelf)
            }
            handler.postDelayed(this, 160)
        }
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) { prepareVideo() }
    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) { updateVideoTransform() }
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean { disposePlayer(); return true }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    private inner class GlassDrawable(
        private val radius: Float,
        private val borderAlpha: Int,
        private val tintAlpha: Int = 51,
    ) : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val clip = Path()
        override fun draw(canvas: Canvas) {
            val rect = RectF(bounds)
            val r = radius * layoutScale
            clip.reset(); clip.addRoundRect(rect, r, r, Path.Direction.CW)
            val save = canvas.save()
            canvas.clipPath(clip)
            blurredBitmap?.let { bitmap ->
                val owner = callback as? View
                if (owner != null) {
                    val location = Rect(0, 0, owner.width, owner.height)
                    offsetDescendantRectToMyCoords(owner, location)
                    paint.color = Color.WHITE
                    canvas.drawBitmap(bitmap, null, RectF(-location.left.toFloat(), -location.top.toFloat(),
                        width - location.left.toFloat(), height - location.top.toFloat()), paint)
                }
            }
            paint.color = Color.argb(tintAlpha, 255, 255, 255)
            canvas.drawRect(rect, paint)
            canvas.restoreToCount(save)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = max(.5f, layoutScale)
            paint.color = Color.argb(borderAlpha, 199, 201, 202)
            rect.inset(paint.strokeWidth / 2, paint.strokeWidth / 2)
            canvas.drawRoundRect(rect, r, r, paint)
            paint.style = Paint.Style.FILL
        }
        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
