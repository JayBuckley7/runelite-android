package net.runelite.mp.ui.bridge

import android.content.Context
import android.os.Build
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.mutableStateOf
import java.lang.ref.WeakReference

object SoftKeyboardController {
    val isKeyboardVisible = mutableStateOf(false)
    private var attachedViewRef: WeakReference<KeyboardInputView>? = null

    var isPasswordProtectedInput = true
        private set

    fun updatePasswordMode(restricted: Boolean) {
        val view = attachedViewRef?.get()
        if (view == null) {
            isPasswordProtectedInput = restricted
            return
        }
        view.post {
            if (isPasswordProtectedInput != restricted) {
                isPasswordProtectedInput = restricted
                val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.restartInput(view)
            }
        }
    }

    fun registerView(view: KeyboardInputView) {
        attachedViewRef = WeakReference(view)
    }

    fun unregisterView(view: KeyboardInputView) {
        if (attachedViewRef?.get() === view) {
            attachedViewRef = null
        }
    }

    fun show() {
        val view = attachedViewRef?.get() ?: return
        view.post {
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(view, InputMethodManager.SHOW_FORCED)
            try {
                val activity = net.runelite.mp.MainActivity.instance
                androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.ime())
            } catch (t: Throwable) {
                // ignore
            }
            isKeyboardVisible.value = true
        }
    }

    fun hide() {
        val view = attachedViewRef?.get() ?: return
        view.post {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            try {
                val activity = net.runelite.mp.MainActivity.instance
                androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                // Put immersive mode back. MainActivity.onWindowFocusChanged covers the
                // usual case, but it only fires if the IME actually took window focus —
                // with adjustPan and some IMEs it doesn't, and then nothing would restore
                // the system bars that showing the keyboard knocked out on API < 30.
                // Posted rather than called inline so it lands after the IME has finished
                // animating away and stopped touching the flags itself.
                activity.window.decorView.post { net.runelite.mp.MainActivity.hideSystemUI() }
            } catch (t: Throwable) {
                // ignore
            }
            isKeyboardVisible.value = false
        }
    }

    fun toggle() {
        if (isKeyboardVisible.value) {
            hide()
        } else {
            show()
        }
    }
}

class KeyboardInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SoftKeyboardController.registerView(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        SoftKeyboardController.unregisterView(this)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = if (SoftKeyboardController.isPasswordProtectedInput) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN

        return object : BaseInputConnection(this, true) {
            private var currentComposing = ""

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val newText = text?.toString() ?: ""
                syncComposing(newText)
                return true
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val newText = text?.toString() ?: ""
                if (currentComposing.isNotEmpty()) {
                    syncComposing(newText)
                    currentComposing = ""
                } else if (newText.isNotEmpty()) {
                    KeyDispatch.sendString(newText)
                }
                if (newText.contains('\n') || newText.contains('\r')) {
                    SoftKeyboardController.hide()
                }
                return true
            }

            override fun finishComposingText(): Boolean {
                currentComposing = ""
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                currentComposing = ""
                for (i in 0 until beforeLength) {
                    KeyDispatch.sendBackspace()
                }
                return true
            }

            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
                return if (currentComposing.length > n) {
                    currentComposing.substring(currentComposing.length - n)
                } else {
                    currentComposing
                }
            }

            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
                return ""
            }

            override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
                currentComposing = ""
                val res = KeyDispatch.sendAndroidKeyEvent(event)
                if (event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP) {
                    SoftKeyboardController.hide()
                }
                return res
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                currentComposing = ""
                when (actionCode) {
                    EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_UNSPECIFIED -> {
                        KeyDispatch.sendEnter()
                        SoftKeyboardController.hide()
                        return true
                    }
                }
                return super.performEditorAction(actionCode)
            }

            private fun syncComposing(newText: String) {
                if (newText == currentComposing) return

                var commonPrefixLen = 0
                val minLen = minOf(currentComposing.length, newText.length)
                while (commonPrefixLen < minLen && currentComposing[commonPrefixLen] == newText[commonPrefixLen]) {
                    commonPrefixLen++
                }

                val backspacesNeeded = currentComposing.length - commonPrefixLen
                for (i in 0 until backspacesNeeded) {
                    KeyDispatch.sendBackspace()
                }

                val newSuffix = newText.substring(commonPrefixLen)
                if (newSuffix.isNotEmpty()) {
                    KeyDispatch.sendString(newSuffix)
                }

                currentComposing = newText
            }
        }
    }
}
