package com.atrishub.atriscast.receiver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReceiverRuntimeTest {
    @Test
    fun concurrentStateUpdatesDoNotLoseCounters() {
        ReceiverRuntime.replace(ReceiverState())
        val workers = Executors.newFixedThreadPool(8)
        val updates = 1_000
        val completed = CountDownLatch(updates)

        try {
            repeat(updates) {
                workers.execute {
                    try {
                        ReceiverRuntime.update { state ->
                            state.copy(mediaBytesReceived = state.mediaBytesReceived + 1L)
                        }
                    } finally {
                        completed.countDown()
                    }
                }
            }

            assertTrue("Concurrent receiver updates did not finish in time", completed.await(15, TimeUnit.SECONDS))
            assertEquals(updates.toLong(), ReceiverRuntime.state.value.mediaBytesReceived)
        } finally {
            workers.shutdownNow()
            ReceiverRuntime.replace(ReceiverState())
        }
    }
}
