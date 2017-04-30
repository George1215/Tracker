package benson.tracker.ui.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import benson.tracker.R
import benson.tracker.common.utils.inflate
import benson.tracker.ui.activities.TrackedUserScreen
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.tracked_user_tab_info_monthly.*
import kotlinx.android.synthetic.main.tracked_user_tab_info_monthly.month_idle_chart as pieChart

class TrackedUsersMonthlyTab : Fragment() {

    var idleTime = 0L
    var movinTime = 0L

    val userName: String by lazy { (activity as TrackedUserScreen).trackedUserName as String }

    lateinit var ddy: ArrayList<Float>

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return container?.inflate(R.layout.tracked_user_tab_info_monthly)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ddy = ArrayList(2)

        val databaseRef = FirebaseDatabase.getInstance().reference

        var dif: Double
        databaseRef.child("gps").child(userName.plus("_mm")).child("distance_walked").addValueEventListener(object : ValueEventListener {
            override fun onCancelled(err: DatabaseError?) {

            }

            override fun onDataChange(ds: DataSnapshot?) {
                if (ds?.exists()!!) {
                    dif = ds.value as Double
                    monthly_distance_walked.text = dif.format(1).plus(" KM")
                } else {
                    monthly_distance_walked.text = "0 KM"
                }
            }
        })

        databaseRef.child("gps").child(userName.plus("_mm")).child("average_speed").addValueEventListener(object : ValueEventListener {
            override fun onCancelled(de: DatabaseError?) {

            }

            override fun onDataChange(ds: DataSnapshot?) {
                if (ds?.exists()!!) {
                    val averageSpeed = ds.value as Double

                    month_average_speed.text = averageSpeed.format(2).plus(" MPH")
                } else {
                    month_average_speed.text = "0 MPH"
                }
            }
        })

        databaseRef.child("gps").child(userName.plus("_mm")).child("idle_time").addValueEventListener(object : ValueEventListener {
            override fun onCancelled(de: DatabaseError?) {

            }

            override fun onDataChange(ds: DataSnapshot?) {
                if (ds?.exists()!!) {
                    idleTime = ds.value as Long
                    if (ddy.size != 0) {
                        ddy[0] = idleTime.toFloat()
                    } else {
                        ddy.add(0, idleTime.toFloat())
                    }
                } else {
                    //ddy.add(0f)
                }
            }
        })

        databaseRef.child("gps").child(userName.plus("_mm")).child("movin_time").addValueEventListener(object : ValueEventListener {
            override fun onCancelled(de: DatabaseError?) {

            }

            override fun onDataChange(ds: DataSnapshot?) {
                if (ds?.exists()!!) {
                    movinTime = ds.value as Long
                    if (ddy.size > 1) {
                        ddy[1] = movinTime.toFloat()
                    } else {
                        ddy.add(1, movinTime.toFloat())
                    }
                    initPie()
                } else {
                    //ddy.add(0f)
                    initPie()
                }
            }
        })

    }

    fun initPie() {
        val desc: Description = Description()

        desc.text = "Idle/Moving time for $userName"
        pieChart.description = desc
        pieChart.holeRadius = 25f
        pieChart.setDrawEntryLabels(false)
        pieChart.setTransparentCircleAlpha(0)

        addData()
    }

    private fun addData() {
        val entries: ArrayList<PieEntry> = ArrayList()
        ddy.indices.mapTo(entries) { PieEntry(ddy[it], it) }

        if (entries.isEmpty()) {
            pieChart.visibility = View.INVISIBLE
            no_data_available.visibility = View.VISIBLE
        } else {
            pieChart.visibility = View.VISIBLE
            no_data_available.visibility = View.INVISIBLE
        }

        val ds: PieDataSet = PieDataSet(entries, "Idle/Moving time")
        ds.sliceSpace = 2f
        ds.valueTextSize = 0f

        val colors: ArrayList<Int> = ArrayList()
        colors.add(ContextCompat.getColor(context, R.color.colorPrimary))
        colors.add(ContextCompat.getColor(context, R.color.colorOrange))

        ds.colors = colors

        val legend: Legend = pieChart.legend
        legend.form = Legend.LegendForm.CIRCLE

        val pieData: PieData = PieData(ds)
        pieChart.data = pieData
        pieChart.invalidate()
    }

    fun Double.format(digits: Int): String = java.lang.String.format("%.${digits}f", this)
}