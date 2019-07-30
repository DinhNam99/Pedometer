package com.dell.test

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import org.eazegraph.lib.charts.PieChart
import org.eazegraph.lib.models.BarModel
import org.eazegraph.lib.models.PieModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var running = true

    private var todayOffset: Int = 0
    private var total_start: Int = 0
    private var goal: Int = 0
    private var since_boot: Int = 0
    private var total_days: Int = 0
    val formatter = NumberFormat.getInstance(Locale.getDefault())

    internal var sensorManager: SensorManager? = null

    private val DEFAULT_STEPS = 6000
    internal val DEFAULT_STEP_SIZE = if (Locale.getDefault() === Locale.US) 2.5f else 75f
    internal val DEFAULT_STEP_UNIT = if (Locale.getDefault() === Locale.US) "ft" else "cm"

    internal var sliceCurrent = PieModel()
    internal var sliceGoal = PieModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // slice for the steps taken today
        sliceCurrent = PieModel("", 0f, Color.parseColor("#99CC00"))
        piechart.addPieSlice(sliceCurrent)
        // slice for the "missing" steps until reaching the goal
        sliceGoal = PieModel("", DEFAULT_STEPS.toFloat(), Color.parseColor("#CC0000"))
        piechart.addPieSlice(sliceGoal)

        piechart.isDrawValueInPie = false
        piechart.isUsePieRotation = true
        piechart.setOnClickListener(View.OnClickListener {
            running = !running
            stepsDistanceChanged()
        })
        piechart.startAnimation()
    }

    private fun stepsDistanceChanged() {
        if (running) {
            unit.text = "Steps"
        } else {
            var u = this.getSharedPreferences("pedometer", Context.MODE_PRIVATE)
                .getString("stepsize_unit", DEFAULT_STEP_UNIT)
            if (u == "cm") {
                u = "km"
            } else {
                u = "mi"
            }
            unit.text = u
        }
        updatePie()
        updateBars()

    }

    override fun onResume() {
        super.onResume()

        val database = Database(this)
        val dayUtils = DayUtils()
        val db = database.getInstance(this)

        // read todays offset
        if (dayUtils != null) {
            todayOffset = db.getSteps(dayUtils.getToday())
        }

        val prefs = this.getSharedPreferences("pedometer", Context.MODE_PRIVATE)

        goal = prefs.getInt("goal", DEFAULT_STEPS)
        since_boot = db.getCurrentSteps()
        val pauseDifference = since_boot - prefs.getInt("pauseCount", since_boot)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager!!.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor != null) {
            sensorManager!!.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Toast.makeText(this, "Sensor not found", Toast.LENGTH_SHORT).show()
        }
        since_boot -= pauseDifference

        total_start = db.getTotalWithoutToday()
        total_days = db.getDays()
        db.close()
        stepsDistanceChanged()

    }

    override fun onPause() {
        super.onPause()
        running = false
        try {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sm.unregisterListener(this)
        } catch (e: Exception) {
        }

        val database = Database(this)
        val db = database.getInstance(this)
        db.saveCurrentSteps(since_boot)
        db.close()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event!!.values[0] > Integer.MAX_VALUE || event.values[0] == 0f) {
            return
        }
        if (todayOffset == Integer.MIN_VALUE) {
            todayOffset = -event.values[0].toInt()
            val database = Database(this)
            val db = database.getInstance(this)
            db.insertNewDay(DayUtils().getToday(), event.values[0].toInt())
            db.close()
        }
        since_boot = event.values[0].toInt()
        Log.e("STEPS", todayOffset.toString() + "")
        updatePie()
    }

    private fun updatePie() {
        val steps_today = Math.max(todayOffset + since_boot, 0)
        sliceCurrent.value = steps_today.toFloat()

        if (goal - steps_today > 0) {
            if (piechart.getData().size == 1) {
                piechart.addPieSlice(sliceGoal)
            }
            sliceGoal.value = (goal - steps_today).toFloat()
        } else {
            piechart.clearChart()
            piechart.addPieSlice(sliceCurrent)
        }
        piechart.update()
        if (running) {
            steps.text = formatter.format(steps_today.toLong())
            total.text = (formatter.format((total_start + steps_today).toLong()))
            average.text = (formatter.format(((total_start + steps_today) / total_days).toLong()))
        } else {
            // update only every 10 steps when displaying distance
            val prefs = this.getSharedPreferences("pedometer", Context.MODE_PRIVATE)
            val stepsize = prefs.getFloat("stepsize_value", DEFAULT_STEP_SIZE)
            var distance_today = steps_today * stepsize
            var distance_total = (total_start + steps_today) * stepsize
            if (prefs.getString("stepsize_unit", DEFAULT_STEP_UNIT) == "cm") {
                distance_today /= 100000f
                distance_total /= 100000f
            } else {
                distance_today /= 5280f
                distance_total /= 5280f
            }
            steps.text = formatter.format(distance_today.toDouble())
            total.text = (formatter.format(distance_total.toDouble()))
            average.text = (formatter.format((distance_total / total_days).toDouble()))
        }
    }
    private fun updateBars() {
        val df = SimpleDateFormat("E", Locale.getDefault())
        if (barChart.getData().size > 0) barChart.clearChart()
        var steps: Int
        var distance: Float
        var stepsize = DEFAULT_STEP_SIZE
        var stepsize_cm = true
        if (!running) {
            // load some more settings if distance is needed
            val prefs = this.getSharedPreferences("pedometer", Context.MODE_PRIVATE)
            stepsize = prefs.getFloat("stepsize_value", DEFAULT_STEP_SIZE)
            stepsize_cm = prefs.getString("stepsize_unit", DEFAULT_STEP_UNIT) == "cm"
        }
        barChart.setShowDecimal(!running) // show decimal in distance view only
        var bm: BarModel
        val database = Database(this)
        val db = database.getInstance(this)
        val last = db.getLastEntries(8)
        db.close()
        for (i in last.size - 1 downTo 1) {
            val current = last.get(i)
            steps = current.second
            if (steps > 0) {
                bm = BarModel(
                    df.format(Date(current.first)), 0f,
                    if (steps > goal) Color.parseColor("#99CC00") else Color.parseColor("#0099cc")
                )
                if (running) {
                    bm.value = steps.toFloat()
                } else {
                    distance = steps * stepsize
                    if (stepsize_cm) {
                        distance /= 100000f
                    } else {
                        distance /= 5280f
                    }
                    distance = Math.round(distance * 1000) / 1000f // 3 decimals
                    bm.value = distance
                }
                barChart.addBar(bm)
            }
        }
        if (barChart.getData().size > 0) {
            barChart.startAnimation()
        } else {
            barChart.visibility = (View.GONE)
        }
    }

}
