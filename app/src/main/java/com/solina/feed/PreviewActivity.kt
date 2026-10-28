package com.solina.feed

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

/** Shows the feed full screen so it can be tried without the launcher. */
class PreviewActivity : Activity() {

    private lateinit var panel: FeedPanel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        panel = FeedPanel(this)
        panel.onCloseRequested = { finish() }
        setContentView(panel)
    }

    override fun onResume() {
        super.onResume()
        panel.onShown()
    }
}
