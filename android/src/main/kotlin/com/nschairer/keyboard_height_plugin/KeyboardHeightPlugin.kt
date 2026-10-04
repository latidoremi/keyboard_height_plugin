package com.nschairer.keyboard_height_plugin
import android.graphics.Rect
import androidx.annotation.NonNull
import android.view.View
import android.view.ViewTreeObserver
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.EventChannel
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class KeyboardHeightPlugin : FlutterPlugin, EventChannel.StreamHandler, ActivityAware {
    private val keyboardHeightEventChannelName = "keyboardHeightEventChannel"
    private var eventSink: EventChannel.EventSink? = null
    private var eventChannel: EventChannel? = null
    private var activityPluginBinding: ActivityPluginBinding? = null
    private var observedView: View? = null
    private var layoutObserver: ViewTreeObserver? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, keyboardHeightEventChannelName)
        eventChannel?.setStreamHandler(this)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        stopListening()
        activityPluginBinding = null
        eventChannel?.setStreamHandler(null)
        eventChannel = null
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        removeLayoutListener()
        eventSink = events
        attachLayoutListener()
    }

    private fun attachLayoutListener() {
        removeLayoutListener()
        if (eventSink == null) return
        val rootView = activityPluginBinding?.activity?.window?.decorView?.rootView ?: return
        val observer = rootView.viewTreeObserver
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // A dispatched callback can outlive cancellation or replacement.
                if (layoutListener !== this) return
                val sink = eventSink ?: return
                val r = Rect()
                rootView.getWindowVisibleDisplayFrame(r)

                val screenHeight = rootView.height
                val insets = ViewCompat.getRootWindowInsets(rootView)
                val navigationBarHeight = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0;
                val isNavigationBarVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: false
                
                var keypadHeight = screenHeight - r.bottom
                
                if (isNavigationBarVisible) {
                    keypadHeight -= navigationBarHeight
                }

                val displayMetrics = activityPluginBinding?.activity?.resources?.displayMetrics
                val logicalKeypadHeight = keypadHeight / (displayMetrics?.density ?: 1f)

                if (keypadHeight > screenHeight * 0.02) {
                    sink.success(logicalKeypadHeight.toDouble())
                } else {
                    sink.success(0.0)
                }
            }
        }
        observedView = rootView
        layoutObserver = observer
        layoutListener = listener
        observer.addOnGlobalLayoutListener(listener)
    }

    private fun removeLayoutListener() {
        val listener = layoutListener
        val view = observedView
        val observer = layoutObserver
        layoutListener = null
        layoutObserver = null
        observedView = null
        if (listener == null) return

        // Android can merge an observer into a new one when a view attaches.
        val currentObserver = if (observer?.isAlive == true) observer else view?.viewTreeObserver
        if (currentObserver?.isAlive == true) {
            currentObserver.removeOnGlobalLayoutListener(listener)
        }
    }

    private fun stopListening() {
        eventSink = null
        removeLayoutListener()
    }

    override fun onCancel(arguments: Any?) {
        stopListening()
    }

    // Implement ActivityAware methods
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityPluginBinding = binding
        attachLayoutListener()
    }

    override fun onDetachedFromActivityForConfigChanges() {
        removeLayoutListener()
        activityPluginBinding = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        stopListening()
        activityPluginBinding = null
    }
}
