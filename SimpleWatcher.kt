package com.voicetennis.app
import android.text.Editable
import android.text.TextWatcher
class SimpleWatcher(private val changed: () -> Unit) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { changed() }
    override fun afterTextChanged(s: Editable?) {}
}
