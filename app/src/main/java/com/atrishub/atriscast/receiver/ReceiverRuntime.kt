package com.atrishub.atriscast.receiver

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object ReceiverRuntime {
    private lateinit var appContext: Context
    private val mutableState = MutableStateFlow(ReceiverState())
    val state: StateFlow<ReceiverState> = mutableState.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun context(): Context = appContext

    fun update(transform: (ReceiverState) -> ReceiverState) {
        // AirPlay video, audio and control callbacks run on different worker threads. StateFlow's
        // atomic update prevents one callback from overwriting fields published concurrently by
        // another (for example an audio status update racing a video geometry/frame update).
        mutableState.update(transform)
    }

    fun replace(state: ReceiverState) {
        mutableState.value = state
    }
}
