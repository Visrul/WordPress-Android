package org.wordpress.android.util

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wordpress.android.modules.BG_THREAD
import org.wordpress.android.modules.UI_THREAD
import org.wordpress.android.ui.utils.UiHelpers
import org.wordpress.android.util.AppLog.T
import org.wordpress.android.widgets.WPSnackbar
import org.wordpress.android.widgets.WPSnackbarWrapper
import java.util.LinkedList
import java.util.Queue
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

private const val QUEUE_SIZE_LIMIT: Int = 5

@Singleton
class SnackbarSequencer @Inject constructor(
    private val uiHelper: UiHelpers,
    private val wpSnackbarWrapper: WPSnackbarWrapper,
    @Named(BG_THREAD) private val bgDispatcher: CoroutineDispatcher,
    @Named(UI_THREAD) private val mainDispatcher: CoroutineDispatcher
) : CoroutineScope {
    private var job: Job = Job()

    private val snackBarQueue: Queue<SnackbarItem> = LinkedList()

    override val coroutineContext: CoroutineContext
        get() = bgDispatcher + job

    fun enqueue(item: SnackbarItem) {
        synchronized(this@SnackbarSequencer) {
            AppLog.d(T.UTILS, "SnackbarSequencer > New item added")
            if (snackBarQueue.size == QUEUE_SIZE_LIMIT) {
                snackBarQueue.remove()
            }
            snackBarQueue.add(item)
            if (snackBarQueue.size == 1) {
                launch {
                    AppLog.d(T.UTILS, "SnackbarSequencer > invoking start()")
                    start()
                }
            }
        }
    }

    private suspend fun start() {
        while (true) {
            val item = snackBarQueue.peek()
            val context: Activity? = item.info.view.get()?.context as? Activity
            if (context != null && isContextAlive(context)) {
                withContext(mainDispatcher) {
                    prepareSnackBar(context, item)?.show()
                }
                AppLog.d(T.UTILS, "SnackbarSequencer > before delay")
                delay(getSnackbarDurationMs(item))
                AppLog.d(T.UTILS, "SnackbarSequencer > after delay")
            } else {
                AppLog.d(T.UTILS,
                        "SnackbarSequencer > start context was ${if (context == null) "null" else "not alive"}")
            }
            synchronized(this@SnackbarSequencer) {
                if (snackBarQueue.peek() == item) {
                    AppLog.d(T.UTILS, "SnackbarSequencer > item removed from the queue")
                    snackBarQueue.remove()
                }
                if (snackBarQueue.isEmpty()) {
                    AppLog.d(T.UTILS, "SnackbarSequencer > finishing start()")
                    return
                }
            }
        }
    }

    private fun isContextAlive(activity: Activity): Boolean {
        return !activity.isFinishing
    }

    private fun prepareSnackBar(context: Context, item: SnackbarItem): WPSnackbar? {
        return item.info.view.get()?.let { view ->
            val message = uiHelper.getTextOfUiString(context, item.info.textRes)

            val snackbar = wpSnackbarWrapper.make(view, message, item.info.duration)

            item.action?.let { actionInfo ->
                snackbar.setAction(
                        uiHelper.getTextOfUiString(context, actionInfo.textRes),
                        actionInfo.clickListener.get()
                )
            }

            item.callback?.let { callbackinfo ->
                snackbar.addCallback(callbackinfo.snackbarCallback.get())
            }

            AppLog.d(T.UTILS, "SnackbarSequencer > prepareSnackBar message [$message]")

            return snackbar
        } ?: null.also {
            AppLog.e(T.UTILS, "SnackbarSequencer > prepareSnackBar Unexpected null view")
        }
    }
}