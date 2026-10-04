package com.nschairer.keyboard_height_plugin

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class KeyboardHeightPluginTest {
    private val plugin = KeyboardHeightPlugin()
    private val binding = mock(ActivityPluginBinding::class.java)
    private val view = mock(View::class.java)
    private val observer = mock(ViewTreeObserver::class.java)
    private val sink = mock(EventChannel.EventSink::class.java)

    @Before
    fun attachActivity() {
        val activity = mock(Activity::class.java)
        val window = mock(Window::class.java)
        `when`(binding.activity).thenReturn(activity)
        `when`(activity.window).thenReturn(window)
        `when`(window.decorView).thenReturn(view)
        `when`(view.rootView).thenReturn(view)
        `when`(view.viewTreeObserver).thenReturn(observer)
        `when`(observer.isAlive).thenReturn(true)
        plugin.onAttachedToActivity(binding)
    }

    private fun subscribedListener(): ViewTreeObserver.OnGlobalLayoutListener {
        val captor = ArgumentCaptor.forClass(ViewTreeObserver.OnGlobalLayoutListener::class.java)
        verify(observer).addOnGlobalLayoutListener(captor.capture())
        return captor.value
    }

    @Test
    fun cancelRemovesListenerAndIgnoresLateLayout() {
        plugin.onListen(null, sink)
        val listener = subscribedListener()
        plugin.onCancel(null)
        plugin.onCancel(null)
        verify(observer, times(1)).removeOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        verifyNoInteractions(sink)
    }

    @Test
    fun replacingSubscriptionRemovesOldListener() {
        plugin.onListen(null, sink)
        val oldListener = subscribedListener()
        val newSink = mock(EventChannel.EventSink::class.java)
        plugin.onListen(null, newSink)
        verify(observer).removeOnGlobalLayoutListener(oldListener)
        oldListener.onGlobalLayout()
        verifyNoInteractions(sink, newSink)
        plugin.onCancel(null)
        verify(observer, times(2)).removeOnGlobalLayoutListener(any())
    }

    @Test
    fun activityDetachReleasesListenerAndSubscription() {
        plugin.onListen(null, sink)
        val listener = subscribedListener()
        plugin.onDetachedFromActivity()
        verify(observer).removeOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        verifyNoInteractions(sink)
        plugin.onAttachedToActivity(binding)
        verify(observer, times(1)).addOnGlobalLayoutListener(any())
    }

    @Test
    fun configurationChangeReattachesOnlyAnActiveSubscription() {
        plugin.onListen(null, sink)
        val listener = subscribedListener()
        plugin.onDetachedFromActivityForConfigChanges()
        verify(observer).removeOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        verifyNoInteractions(sink)
        plugin.onReattachedToActivityForConfigChanges(binding)
        verify(observer, times(2)).addOnGlobalLayoutListener(any())
        plugin.onDetachedFromActivityForConfigChanges()
        plugin.onCancel(null)
        plugin.onReattachedToActivityForConfigChanges(binding)
        verify(observer, times(2)).addOnGlobalLayoutListener(any())
    }

    @Test
    fun engineDetachRemovesListener() {
        plugin.onListen(null, sink)
        val listener = subscribedListener()
        plugin.onDetachedFromEngine(mock(FlutterPlugin.FlutterPluginBinding::class.java))
        verify(observer).removeOnGlobalLayoutListener(listener)
        listener.onGlobalLayout()
        verifyNoInteractions(sink)
    }

    @Test
    fun cancelUsesCurrentObserverWhenOriginalObserverIsDead() {
        plugin.onListen(null, sink)
        val listener = subscribedListener()
        val replacement = mock(ViewTreeObserver::class.java)
        `when`(observer.isAlive).thenReturn(false)
        `when`(view.viewTreeObserver).thenReturn(replacement)
        `when`(replacement.isAlive).thenReturn(true)
        plugin.onCancel(null)
        verify(observer, never()).removeOnGlobalLayoutListener(any())
        verify(replacement).removeOnGlobalLayoutListener(listener)
    }
}
