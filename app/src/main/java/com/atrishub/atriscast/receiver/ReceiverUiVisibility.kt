package com.atrishub.atriscast.receiver

/** Process-local visibility signal shared by MainActivity and the foreground receiver service. */
object ReceiverUiVisibility {
    @Volatile private var visible = false
    @Volatile private var listener: ((Boolean) -> Unit)? = null

    fun isVisible(): Boolean = visible

    fun setVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        listener?.invoke(value)
    }

    fun setListener(callback: ((Boolean) -> Unit)?) {
        listener = callback
        callback?.invoke(visible)
    }
}
