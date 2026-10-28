package com.solina.feed

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Settings: city for the weather, news sources, and a preview of the feed. */
class MainActivity : Activity() {

    private lateinit var cityField: EditText
    private lateinit var feedsField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (20 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }

        column.addView(label("Solina Feed", 26f, bold = true))
        column.addView(
            label(
                "فید همراه Solina Launcher: تقویم شمسی، آب‌وهوا، کارها، یادداشت و اخبار.\n\n" +
                    "برای فعال‌سازی: در Solina Launcher وارد تنظیمات شوید، بخش Home screen، " +
                    "گزینه‌ی Show feed را روشن کنید و در Feed provider مقدار Solina Feed را انتخاب کنید. " +
                    "سپس در صفحه‌ی اصلی به راست بکشید.",
                14f,
            ),
        )

        column.addView(label("شهر (برای آب‌وهوا)", 15f, bold = true))
        cityField = EditText(this).apply {
            setText(FeedStore.city(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }
        column.addView(cityField)

        column.addView(label("منابع خبری (هر خط یک آدرس https، RSS یا Atom)", 15f, bold = true))
        feedsField = EditText(this).apply {
            setText(FeedStore.feedsText(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            textDirection = View.TEXT_DIRECTION_LTR
        }
        column.addView(feedsField)

        val save = Button(this).apply {
            text = "ذخیره"
            setOnClickListener {
                FeedStore.setCity(this@MainActivity, cityField.text.toString())
                FeedStore.setFeedsText(this@MainActivity, feedsField.text.toString())
                Toast.makeText(this@MainActivity, "ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }
        val preview = Button(this).apply {
            text = "پیش‌نمایش فید"
            setOnClickListener {
                FeedStore.setCity(this@MainActivity, cityField.text.toString())
                FeedStore.setFeedsText(this@MainActivity, feedsField.text.toString())
                startActivity(Intent(this@MainActivity, PreviewActivity::class.java))
            }
        }
        column.addView(save)
        column.addView(preview)

        setContentView(
            ScrollView(this).apply {
                addView(
                    column,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
                )
            },
        )
    }

    private fun label(value: String, size: Float, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(0, 16, 0, 8)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
}
