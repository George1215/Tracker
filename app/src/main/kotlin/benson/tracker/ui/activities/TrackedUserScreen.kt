package benson.tracker.ui.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import benson.tracker.R
import benson.tracker.common.utils.getMarkerBitmapFromView
import benson.tracker.ui.adapters.TrackedUsersTabs
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.screen_tracked_user.*

class TrackedUserScreen : AppCompatActivity(), OnMapReadyCallback {

    lateinit var gMap: GoogleMap
    var trackedUserName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_tracked_user)

        setSupportActionBar(app_toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        app_toolbar.setNavigationOnClickListener { finish() }

        trackedUserName = intent.extras.getString("tracked_user_name")
        app_toolbar.title = trackedUserName.plus("'s Report")

        val supportMap: SupportMapFragment =
                supportFragmentManager.findFragmentById(R.id.tracked_user_map) as SupportMapFragment

        supportMap.getMapAsync(this)

        button_full_map.setOnClickListener {
            startActivity(Intent(this, TrackedUserFullView::class.java).putExtra("tracked_user_name", trackedUserName))
        }

        tracked_user_pager.adapter = TrackedUsersTabs(supportFragmentManager)
        tracked_user_tabs.setupWithViewPager(tracked_user_pager)
    }

    override fun onMapReady(map: GoogleMap?) {
        if (null != map) {
            gMap = map
            //gMap.uiSettings.setAllGesturesEnabled(true)

            val dbRef = FirebaseDatabase.getInstance().reference

            var mk: Marker? = null
            dbRef.child("coords").child("user_latitude").child(trackedUserName).addValueEventListener(object : ValueEventListener {

                override fun onCancelled(de: DatabaseError?) {

                }

                override fun onDataChange(snap: DataSnapshot?) {
                    val latitude = snap?.value
                    if (latitude != null) {
                        dbRef.child("coords").child("user_longitude").child(trackedUserName).addValueEventListener(object : ValueEventListener {
                            override fun onCancelled(de: DatabaseError?) {

                            }

                            override fun onDataChange(snap: DataSnapshot?) {
                                val longitude = snap?.value

                                mk?.remove()

                                mk = gMap.addMarker(
                                        MarkerOptions()
                                                .title(trackedUserName)
                                                .position(LatLng(latitude as Double, longitude as Double))
                                                .icon(BitmapDescriptorFactory.fromBitmap(applicationContext.getMarkerBitmapFromView(R.drawable.default_avatar)))
                                )

                                gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mk?.position, 14f))
                            }
                        })
                    }
                }
            })
        }
    }

}
