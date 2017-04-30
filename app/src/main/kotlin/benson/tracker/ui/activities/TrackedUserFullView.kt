package benson.tracker.ui.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import benson.tracker.R
import benson.tracker.common.utils.getMarkerBitmapFromView
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
import kotlinx.android.synthetic.main.screen_tracked_user_full.*

class TrackedUserFullView : AppCompatActivity(), OnMapReadyCallback {

    var gMap: GoogleMap? = null
    var trackedUserName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_tracked_user_full)

        trackedUserName = intent.extras.getString("tracked_user_name")

        setSupportActionBar(app_toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        app_toolbar.setNavigationOnClickListener { finish() }
        app_toolbar.title = trackedUserName.plus("\'s Report")

        val supportMap: SupportMapFragment =
                supportFragmentManager.findFragmentById(R.id.tracked_full_map) as SupportMapFragment

        supportMap.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap?) {
        gMap = map

        val dbRef = FirebaseDatabase.getInstance().reference
        var mk: Marker? = null

        if (trackedUserName != null) {
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

                                mk = gMap?.addMarker(
                                        MarkerOptions()
                                                .title(trackedUserName)
                                                .position(LatLng(latitude as Double, longitude as Double))
                                                .icon(BitmapDescriptorFactory.fromBitmap(applicationContext.getMarkerBitmapFromView(R.drawable.default_avatar)))
                                )

                                gMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(mk?.position, 14f))
                            }
                        })
                    }
                }
            })
        }
    }
}