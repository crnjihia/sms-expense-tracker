// path: app/src/androidTest/java/com/example/smsexpensertracker/benchmark/BaselineProfileGenerator.kt
package com.example.smsexpensertracker.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Automates critical user journeys on a physical Android device or rooted emulator
 * to generate optimized DEX Baseline Profiles for ART pre-compilation.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "com.example.smsexpensertracker",
            includeInStartupProfile = true
        ) {
            // Cold startup to Home screen
            pressHome()
            startActivityAndWait()

            // Navigate through key screens to capture Compose render & Room query code paths
            device.waitForIdle()
        }
    }
}

