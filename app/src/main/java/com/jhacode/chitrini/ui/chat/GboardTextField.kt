package com.jhacode.chitrini.ui.chat

import android.content.Context
import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.compose.material3.MaterialTheme

private const val TAG = "KeyboardTrace"

@Composable
fun GboardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onMediaSelected: (Uri, String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    // 🔥 Native View instance kept alive across recompositions
    AndroidView(
        factory = { context ->
            Log.d(TAG, "Factory: Creating GboardEditText")
            GboardEditText(context).apply {
                this.onMediaSelected = onMediaSelected
                hint = placeholder
                setHintTextColor(hintColor)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(textColor)
                
                val density = resources.displayMetrics.density
                setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
                
                isFocusable = true
                isFocusableInTouchMode = true
                
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        val str = s?.toString() ?: ""
                        // 🛡️ Loop Protection: Only fire if user typed it
                        if (!isInternalUpdate && str != value) {
                            Log.d(TAG, "Native -> Compose: \"$str\"")
                            onValueChange(str)
                        }
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })

                setOnFocusChangeListener { _, hasFocus ->
                    Log.d(TAG, "Focus Changed: $hasFocus")
                    if (hasFocus) {
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
            }
        },
        update = { view ->
            // 🛡️ Compose -> Native Sync
            if (view.text.toString() != value) {
                Log.d(TAG, "Compose -> Native: \"$value\"")
                view.isInternalUpdate = true
                view.setText(value)
                view.setSelection(value.length)
                view.isInternalUpdate = false
            }
            
            // Sync colors
            if (view.currentTextColor != textColor) view.setTextColor(textColor)
            if (view.hintTextColors.defaultColor != hintColor) view.setHintTextColor(hintColor)
        },
        modifier = modifier
    )
}

private class GboardEditText(context: Context) : AppCompatEditText(context) {
    
    var onMediaSelected: ((Uri, String) -> Unit)? = null
    var isInternalUpdate = false

    init {
        // Ensure the view is always ready for interaction
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = when(event.action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> event.action.toString()
        }
        
        Log.d(TAG, "Touch Action: $action (isFocused=$isFocused)")
        
        // 🔥 Architectural Fix: Request focus immediately on the first finger touch (ACTION_DOWN)
        // This bypasses any Compose interception that might delay focus until ACTION_UP.
        if (event.action == MotionEvent.ACTION_DOWN) {
            if (!isFocused) {
                Log.d(TAG, "Immediate Focus Request on ACTION_DOWN")
                requestFocus()
            }
        }
        
        return super.onTouchEvent(event)
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        EditorInfoCompat.setContentMimeTypes(outAttrs, arrayOf("image/gif", "image/png", "image/jpeg"))

        val callback = InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, _ ->
            val readPermission = (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0
            if (readPermission) {
                try {
                    inputContentInfo.requestPermission()
                } catch (e: Exception) {
                    return@OnCommitContentListener false
                }
            }
            onMediaSelected?.invoke(inputContentInfo.contentUri, inputContentInfo.description.getMimeType(0) ?: "image/gif")
            true
        }
        return InputConnectionCompat.createWrapper(ic, outAttrs, callback)
    }

    override fun onCheckIsTextEditor(): Boolean = true
}
