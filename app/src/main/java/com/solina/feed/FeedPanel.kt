package com.solina.feed

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar
import java.util.concurrent.Executors

/**
 * The feed page: Jalali date and occasion, weather, tasks, note and news.
 * Built in code, right-to-left. Used both inside the launcher overlay and in the preview screen.
 */
class FeedPanel(context: Context) : FrameLayout(context) {

    private val dark =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    private val cBg = if (dark) 0xFF111318.toInt() else 0xFFF3F4F7.toInt()
    private val cCard = if (dark) 0xFF1D2027.toInt() else Color.WHITE
    private val cText = if (dark) 0xFFECEEF2.toInt() else 0xFF15171C.toInt()
    private val cSub = if (dark) 0xFF9AA1AE.toInt() else 0xFF5B6270.toInt()
    private val cAccent = if (dark) 0xFF4DD0C4.toInt() else 0xFF00897B.toInt()
    private val cHoliday = if (dark) 0xFFFF8A80.toInt() else 0xFFC62828.toInt()

    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val dateBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val weatherBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val tasksBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val newsBox = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var lastRefresh = 0L
    private var tasks: MutableList<Task> = FeedStore.tasks(context)
    private val noteSaver = Runnable { FeedStore.setNote(context, noteField.text.toString()) }
    private lateinit var noteField: EditText

    /** Called when the user asks to close the feed (close button or back key). */
    var onCloseRequested: (() -> Unit)? = null

    init {
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        setBackgroundColor(cBg)
        isClickable = true // Swallow touches so they never reach the launcher underneath.
        isFocusableInTouchMode = true

        val pad = dp(16)
        content.setPadding(pad, dp(32), pad, dp(24))
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        buildHeader()
        content.addView(wrap(dateBox))
        content.addView(wrap(weatherBox))
        buildTasks()
        buildNote()
        content.addView(sectionTitle("اخبار"))
        content.addView(newsBox)

        renderDate()
        renderTasks()
    }

    /** Call when the feed becomes visible. Refreshes weather and news if the data is stale. */
    fun onShown() {
        renderDate()
        refresh(force = false)
    }

    fun refresh(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefresh < 10 * 60 * 1000) return
        lastRefresh = now
        val city = FeedStore.city(context)
        val feeds = FeedStore.feeds(context)
        io.execute {
            val weather = try {
                FeedNet.weather(context, city)
            } catch (e: Exception) {
                null
            }
            val news = FeedNet.news(feeds)
            ui.post {
                renderWeather(weather)
                renderNews(news, feeds.isEmpty())
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onCloseRequested?.invoke()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ui.removeCallbacks(noteSaver)
        if (::noteField.isInitialized) FeedStore.setNote(context, noteField.text.toString())
    }

    // region Sections

    private fun buildHeader() {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            text("Solina Feed", 22f, cText, bold = true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        val refresh = text("بروزرسانی", 13f, cAccent).apply {
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { refresh(force = true) }
        }
        val close = text("✕", 20f, cSub).apply {
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { onCloseRequested?.invoke() }
        }
        row.addView(refresh)
        row.addView(close)
        content.addView(row)
        content.addView(space(8))
    }

    private fun buildTasks() {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text("کارهای امروز", 16f, cText, bold = true))
        box.addView(space(6))
        box.addView(tasksBox)
        val input = EditText(context).apply {
            hint = "کار جدید…"
            textSize = 15f
            setTextColor(cText)
            setHintTextColor(cSub)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            maxLines = 1
        }
        input.setOnEditorActionListener { v, _, _ ->
            addTask(v.text.toString())
            v.text = ""
            true
        }
        box.addView(input)
        content.addView(wrap(box))
    }

    private fun addTask(value: String) {
        val t = value.trim()
        if (t.isEmpty()) return
        tasks.add(Task(t, false))
        FeedStore.setTasks(context, tasks)
        renderTasks()
    }

    private fun buildNote() {
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text("یادداشت سریع", 16f, cText, bold = true))
        noteField = EditText(context).apply {
            hint = "هر چیزی که می‌خواهید یادتان بماند…"
            textSize = 15f
            setTextColor(cText)
            setHintTextColor(cSub)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setText(FeedStore.note(context))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    ui.removeCallbacks(noteSaver)
                    ui.postDelayed(noteSaver, 600)
                }
            })
        }
        box.addView(noteField)
        content.addView(wrap(box))
    }

    // endregion

    // region Rendering

    private fun renderDate() {
        dateBox.removeAllViews()
        val now = Calendar.getInstance()
        val d = Jalali.fromCalendar(now)
        dateBox.addView(text(Jalali.format(now), 20f, cText, bold = true))
        val greg = android.text.format.DateFormat.format("d MMMM yyyy", now).toString()
        dateBox.addView(text(greg, 13f, cSub))
        val occasion = Occasions.of(d)
        if (occasion != null) {
            dateBox.addView(space(4))
            val label = if (occasion.holiday) "${occasion.title} (تعطیل)" else occasion.title
            dateBox.addView(text(label, 14f, if (occasion.holiday) cHoliday else cAccent))
        } else if (now.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
            dateBox.addView(space(4))
            dateBox.addView(text("جمعه (تعطیل)", 14f, cHoliday))
        }
    }

    private fun renderWeather(w: Weather?) {
        weatherBox.removeAllViews()
        if (w == null) {
            weatherBox.addView(text("آب‌وهوا در دسترس نیست", 14f, cSub))
            weatherBox.addView(text("اینترنت یا نام شهر را در تنظیمات بررسی کنید", 12f, cSub))
            return
        }
        weatherBox.addView(text(w.city, 14f, cSub))
        weatherBox.addView(text(Jalali.digits("${w.temp}°  ${w.description}"), 22f, cText, bold = true))
        weatherBox.addView(text(Jalali.digits("حداقل ${w.min}°   حداکثر ${w.max}°"), 13f, cSub))
    }

    private fun renderTasks() {
        tasksBox.removeAllViews()
        if (tasks.isEmpty()) {
            tasksBox.addView(text("کاری ثبت نشده است", 13f, cSub))
            return
        }
        for ((index, task) in tasks.withIndex()) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val box = CheckBox(context).apply {
                isChecked = task.done
                setOnCheckedChangeListener { _, checked ->
                    tasks[index] = Task(task.text, checked)
                    FeedStore.setTasks(context, tasks)
                    renderTasks()
                }
            }
            val label = text(task.text, 15f, if (task.done) cSub else cText).apply {
                if (task.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }
            val delete = text("✕", 15f, cSub).apply {
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener {
                    tasks.removeAt(index)
                    FeedStore.setTasks(context, tasks)
                    renderTasks()
                }
            }
            row.addView(box)
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(delete)
            tasksBox.addView(row)
        }
    }

    private fun renderNews(items: List<NewsItem>, noFeeds: Boolean) {
        newsBox.removeAllViews()
        if (items.isEmpty()) {
            val msg = if (noFeeds) "هیچ منبع خبری تنظیم نشده است" else "خبری دریافت نشد؛ اتصال اینترنت را بررسی کنید"
            newsBox.addView(wrap(text(msg, 14f, cSub)))
            return
        }
        for (item in items) {
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            col.addView(text(item.title, 15f, cText).apply { maxLines = 3 })
            if (item.source.isNotEmpty()) col.addView(text(item.source, 12f, cSub))
            col.setOnClickListener { open(item.link) }
            newsBox.addView(wrap(col))
        }
    }

    private fun open(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            // No browser available.
        }
    }

    // endregion

    // region View helpers

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun sectionTitle(value: String): TextView =
        text(value, 16f, cText, bold = true).apply { setPadding(dp(4), dp(8), dp(4), dp(6)) }

    private fun space(heightDp: Int): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
    }

    /** Puts [child] in a rounded card with a bottom margin. */
    private fun wrap(child: View): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(cCard)
                cornerRadius = dp(18).toFloat()
            }
            addView(child)
        }
        card.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(12) }
        return card
    }

    // endregion
}
