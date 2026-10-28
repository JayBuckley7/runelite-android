package net.runelite.mp.ui.bridge

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
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
                view.resetConnection()
                if (view.isSessionOpen) imm?.restartInput(view)
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
        attachedViewRef?.get()?.let { view -> view.post { view.openSession() } }
    }

    fun hide() {
        attachedViewRef?.get()?.let { view -> view.post { view.endSession() } }
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

    var isSessionOpen = false
        private set
    private var connectionGeneration = 0
    private var imeWasVisible = false

    init {
        isFocusable = false
        isFocusableInTouchMode = false
    }

    fun resetConnection() { connectionGeneration++ }

    fun openSession() {
        if (isSessionOpen || !isAttachedToWindow || !hasWindowFocus()) return
        resetConnection()
        isSessionOpen = true
        isFocusableInTouchMode = true
        isFocusable = true
        if (!requestFocus()) { endSession(); return }
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.restartInput(this)
        imm?.showSoftInput(this, 0)
    }

    fun endSession() {
        if (!isSessionOpen) return
        isSessionOpen = false
        resetConnection()
        imeWasVisible = false
        SoftKeyboardController.isKeyboardVisible.value = false
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
        isFocusable = false
        isFocusableInTouchMode = false
        post { net.runelite.mp.MainActivity.hideSystemUI() }
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val visible = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
            .isVisible(WindowInsetsCompat.Type.ime())
        if (isSessionOpen) {
            SoftKeyboardController.isKeyboardVisible.value = visible
            if (imeWasVisible && !visible) endSession()
            else imeWasVisible = visible
        }
        return super.onApplyWindowInsets(insets)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) endSession()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SoftKeyboardController.registerView(this)
    }

    override fun onDetachedFromWindow() {
        endSession()
        super.onDetachedFromWindow()
        SoftKeyboardController.unregisterView(this)
    }

    override fun onCheckIsTextEditor(): Boolean = isSessionOpen

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (!isSessionOpen) return null
        val generation = ++connectionGeneration
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
            private var closed = false
            private fun active() = !closed && isSessionOpen && generation == connectionGeneration

            override fun closeConnection() {
                closed = true
                currentComposing = ""
                super.closeConnection()
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!active()) return false
                val newText = text?.toString() ?: ""
                syncComposing(newText)
                return true
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (!active()) return false
                val newText = text?.toString() ?: ""
                if (currentComposing.isNotEmpty()) {
                    syncComposing(newText)
                    currentComposing = ""
                } else if (newText.isNotEmpty()) {
                    KeyDispatch.sendString(newText)
                }
                if (newText.contains('\n') || newText.contains('\r')) {
                    endSession()
                }
                return true
            }

            override fun finishComposingText(): Boolean {
                if (!active()) return false
                currentComposing = ""
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (!active()) return false
                currentComposing = ""
                for (i in 0 until beforeLength) {
                    KeyDispatch.sendBackspace()
                }
                return true
            }

            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
                if (!active() || n <= 0) return ""
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
                if (!active()) return false
                currentComposing = ""
                val res = KeyDispatch.sendAndroidKeyEvent(event)
                if (event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_UP) {
                    endSession()
                }
                return res
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                if (!active()) return false
                currentComposing = ""
                when (actionCode) {
                    EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_UNSPECIFIED -> {
                        KeyDispatch.sendEnter()
                        endSession()
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
