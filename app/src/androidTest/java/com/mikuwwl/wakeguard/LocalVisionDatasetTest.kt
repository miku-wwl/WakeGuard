package com.mikuwwl.wakeguard

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.mikuwwl.wakeguard.vision.OnDeviceVisionEngine
import java.io.File
import java.util.zip.ZipFile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Invoked by tools/round2/android_dataset.py using a licensed external dataset. */
@LargeTest
@RunWith(AndroidJUnit4::class)
class LocalVisionDatasetTest {
    @Test fun measureProductionEngine() {
        assumeTrue("External dataset requested", InstrumentationRegistry.getArguments().getString("localDataset") == "1")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.filesDir, "round2")
        val input = File(folder, "input.zip")
        check(input.isFile) { "External dataset ZIP is missing" }
        val measurements = JSONArray()
        ZipFile(input).use { zip ->
            val cases = zip.getInputStream(zip.getEntry("cases.json")).bufferedReader().use { JSONArray(it.readText()) }
            OnDeviceVisionEngine(context).use { engine ->
                for (i in 0 until cases.length()) {
                    val row = cases.getJSONObject(i)
                    val image = zip.getInputStream(zip.getEntry(row.getString("file"))).use {
                        checkNotNull(BitmapFactory.decodeStream(it))
                    }
                    try {
                        if (i == 0) engine.analyze(image) // Exclude warm-up from latency.
                        val start = SystemClock.elapsedRealtimeNanos()
                        val result = engine.analyze(image)
                        val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                        check(result.allScoresValid()) { "Invalid model output for $i" }
                        measurements.put(JSONObject().apply {
                            put("id", row.getString("id"))
                            put("scores", result.toJson())
                            put("latency_ms", elapsed)
                        })
                    } finally {
                        image.recycle()
                    }
                }
            }
        }
        File(folder, "scores.json").writeText(measurements.toString(2))
    }
}
