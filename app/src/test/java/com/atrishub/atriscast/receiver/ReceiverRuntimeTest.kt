package com.atrishub.atriscast.receiver

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReceiverRuntimeTest {
    @Test
    fun concurrentStateUpdatesDoNotLoseCounters() {
        ReceiverRuntime.replace(ReceiverState())
        val workers = Executors.newFixedThreadPool(8)
        val updates = 1_000

        repeat(updates) {
            workers.execute {
                ReceiverRuntime.update { state ->
                    state.copy(mediaBytesReceived = state.mediaBytesReceived + 1L)
                }
            }
        }

        workers.shutdown()
        workers.awaitTermination(5, TimeUnit.SECONDS)

        assertEquals(updates.toLong(), ReceiverRuntime.state.value.mediaBytesReceived)
        ReceiverRuntime.replace(ReceiverState())
    }
}
