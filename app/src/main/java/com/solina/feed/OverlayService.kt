package com.solina.feed

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.RemoteException
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.google.android.libraries.launcherclient.ILauncherOverlay
import com.google.android.libraries.launcherclient.ILauncherOverlayCallback

/**
 * Implements the launcher "overlay" protocol: Solina Launcher binds to this service, hands over its
 * window parameters and then drives the feed page with scroll progress (0 = closed, 1 = open).
 */
class OverlayService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var callback: ILauncherOverlayCallback? = null
    private var windowManager: WindowManager? = null
    private var root: FrameLayout? = null
    private var panel: FeedPanel? = null
    private var params: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var interactive = false
    private var width = 0
    private var resumed = false

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        main.post { detach() }
        return false
    }

    override fun onDestroy() {
        detach()
        super.onDestroy()
    }

    /** Only Solina Launcher may drive the overlay. */
    private fun callerAllowed(): Boolean {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return true
        val packages = packageManager.getPackagesForUid(uid) ?: return false
        return packages.any { it == LAUNCHER_PACKAGE || it.startsWith("$LAUNCHER_PACKAGE.") }
    }

    private val binder = object : ILauncherOverlay.Stub() {

        override fun startScroll() {
            if (!callerAllowed()) return
            main.post { animator?.cancel() }
        }

        override fun onScroll(progress: Float) {
            if (!callerAllowed()) return
            main.post { applyProgress(progress) }
        }

        override fun endScroll() {
            if (!callerAllowed()) return
            main.post { animateTo(if (progress > 0.5f) 1f else 0f, 200L) }
        }

        override fun windowAttached(lp: WindowManager.LayoutParams?, cb: ILauncherOverlayCallback?, flags: Int) {
            if (!callerAllowed()) return
            main.post { attach(lp, cb) }
        }

        override fun windowAttached2(bundle: Bundle?, cb: ILauncherOverlayCallback?) {
            if (!callerAllowed()) return
            bundle?.classLoader = WindowManager.LayoutParams::class.java.classLoader
            val lp = layoutParamsFrom(bundle)
            main.post { attach(lp, cb) }
        }

        override fun windowDetached(isChangingConfigurations: Boolean) {
            if (!callerAllowed()) return
            main.post { detach() }
        }

        override fun closeOverlay(flags: Int) {
            if (!callerAllowed()) return
            main.post { animateTo(0f, durationFor(flags)) }
        }

        override fun openOverlay(flags: Int) {
            if (!callerAllowed()) return
            main.post { animateTo(1f, durationFor(flags)) }
        }

        override fun onPause() {
            if (!callerAllowed()) return
            main.post { setResumed(false) }
        }

        override fun onResume() {
            if (!callerAllowed()) return
            main.post { setResumed(true) }
        }

        override fun setActivityState(flags: Int) {
            if (!callerAllowed()) return
            // Bit 1 (value 2) is set while the launcher is resumed.
            main.post { setResumed((flags and 2) != 0) }
        }

        override fun requestVoiceDetection(start: Boolean) {}

        override fun getVoiceSearchLanguage(): String = ""

        override fun isVoiceDetectionRunning(): Boolean = false

        override fun hasOverlayContent(): Boolean = callerAllowed()

        override fun unusedMethod() {}

        override fun startSearch(data: ByteArray?, bundle: Bundle?): Boolean = false
    }

    @Suppress("DEPRECATION")
    private fun layoutParamsFrom(bundle: Bundle?): WindowManager.LayoutParams? {
        if (bundle == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            bundle.getParcelable("layout_params", WindowManager.LayoutParams::class.java)
        } else {
            bundle.getParcelable("layout_params")
        }
    }

    private fun durationFor(flags: Int): Long {
        // Bit 0 set: the remaining bits hold the animation duration in milliseconds.
        if ((flags and 1) == 0) return 0L
        val ms = (flags shr 2).toLong()
        return if (ms > 0) ms else 200L
    }

    private fun attach(lp: WindowManager.LayoutParams?, cb: ILauncherOverlayCallback?) {
        callback = cb
        if (lp == null || cb == null) return
        detach()
        callback = cb
        try {
            val ctx = ContextThemeWrapper(this, R.style.AppTheme)
            val wm = ctx.getSystemService(WindowManager::class.java)
            width = ctx.resources.displayMetrics.widthPixels

            val p = WindowManager.LayoutParams()
            p.copyFrom(lp)
            p.token = lp.token
            if (p.type == WindowManager.LayoutParams.TYPE_BASE_APPLICATION) {
                p.type = WindowManager.LayoutParams.TYPE_APPLICATION
            }
            p.width = ViewGroup.LayoutParams.MATCH_PARENT
            p.height = ViewGroup.LayoutParams.MATCH_PARENT
            p.x = 0
            p.y = 0
            p.format = PixelFormat.TRANSLUCENT
            p.windowAnimations = 0
            p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            p.flags = FLAGS_CLOSED
            p.setTitle("SolinaFeed")

            val feed = FeedPanel(ctx)
            feed.onCloseRequested = { animateTo(0f, 220L) }
            val container = FrameLayout(ctx)
            container.addView(
                feed,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
            )
            feed.translationX = -width.toFloat()
            container.visibility = View.INVISIBLE

            wm.addView(container, p)
            windowManager = wm
            params = p
            root = container
            panel = feed
            progress = 0f
            interactive = false
            report { it.overlayStatusChanged(STATUS_READY) }
        } catch (t: Throwable) {
            Log.e(TAG, "Could not attach the feed window", t)
            root = null
            panel = null
            report { it.overlayStatusChanged(0) }
        }
    }

    private fun detach() {
        animator?.cancel()
        animator = null
        val view = root
        if (view != null) {
            try {
                windowManager?.removeViewImmediate(view)
            } catch (e: Exception) {
                Log.w(TAG, "Could not remove the feed window", e)
            }
        }
        root = null
        panel = null
        params = null
        progress = 0f
        interactive = false
    }

    private fun setResumed(value: Boolean) {
        resumed = value
        if (!value && progress > 0f) {
            // The launcher left the foreground: close the feed without animation.
            animateTo(0f, 0L)
        } else if (value && progress >= 1f) {
            panel?.onShown()
        }
    }

    private fun applyProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
        val feed = panel ?: return
        feed.translationX = -(1f - progress) * width
        root?.visibility = if (progress <= 0f) View.INVISIBLE else View.VISIBLE
        val open = progress >= 1f
        if (open != interactive) {
            interactive = open
            val p = params
            val view = root
            if (p != null && view != null) {
                p.flags = if (open) FLAGS_OPEN else FLAGS_CLOSED
                try {
                    windowManager?.updateViewLayout(view, p)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update the feed window", e)
                }
                if (open) {
                    feed.requestFocus()
                    feed.onShown()
                }
            }
        }
    }

    private fun animateTo(target: Float, durationMs: Long) {
        animator?.cancel()
        if (panel == null) return
        if (durationMs <= 0L || progress == target) {
            applyProgress(target)
            reportProgress(target)
            return
        }
        val a = ValueAnimator.ofFloat(progress, target)
        a.duration = durationMs
        a.interpolator = DecelerateInterpolator()
        a.addUpdateListener {
            val v = it.animatedValue as Float
            applyProgress(v)
            reportProgress(v)
        }
        a.start()
        animator = a
    }

    private fun reportProgress(value: Float) = report { it.overlayScrollChanged(value) }

    private fun report(block: (ILauncherOverlayCallback) -> Unit) {
        val cb = callback ?: return
        try {
            block(cb)
        } catch (e: RemoteException) {
            Log.w(TAG, "Launcher callback failed", e)
        }
    }

    companion object {
        private const val TAG = "SolinaFeed"
        private const val LAUNCHER_PACKAGE = "com.solina.launcher"
        private const val STATUS_READY = 1

        private const val FLAGS_CLOSED =
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        private const val FLAGS_OPEN =
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    }
}
