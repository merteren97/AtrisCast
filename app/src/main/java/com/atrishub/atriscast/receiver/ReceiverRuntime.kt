package com.atrishub.atriscast.receiver

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ReceiverRuntime {
    private lateinit var appContext: Context
    private val mutableState = MutableStateFlow(ReceiverState())
    val state: StateFlow<ReceiverState> = mutableState.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun context(): Context = appContext

    fun update(transform: (ReceiverState) -> ReceiverState) {
        mutableState.value = transform(mutableState.value)
    }

    fun replace(state: ReceiverState) {
        mutableState.value = state
    }
}
