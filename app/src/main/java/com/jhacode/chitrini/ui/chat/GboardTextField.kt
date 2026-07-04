package com.jhacode.chitrini.ui.chat

import android.net.Uri
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

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

    AndroidView(
        factory = { context ->
            object : AppCompatEditText(context) {
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

                        val uri = inputContentInfo.contentUri
                        val description = inputContentInfo.description
                        val mimeType = if (description.mimeTypeCount > 0) description.getMimeType(0) else "image/gif"
                        
                        onMediaSelected(uri, mimeType)
                        true
                    }
                    return InputConnectionCompat.createWrapper(ic, outAttrs, callback)
                }
            }.apply {
                hint = placeholder
                setHintTextColor(hintColor)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setTextColor(textColor)
                setPadding(0, 0, 0, 0)
                setText(value)
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        onValueChange(s?.toString() ?: "")
                    }
                    override fun afterTextChanged(s: android.text.Editable?) {}
                })
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setHintTextColor(hintColor)
            if (view.text.toString() != value) {
                view.setText(value)
                view.setSelection(value.length)
            }
        },
        modifier = modifier
    )
}
