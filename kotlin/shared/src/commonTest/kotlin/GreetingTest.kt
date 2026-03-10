package com.blackbean.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class GreetingTest {
    @Test
    fun testGreeting() {
        val greeting = Greeting()
        assertEquals("Hello from Kotlin Multiplatform!", greeting.greet())
    }
}
