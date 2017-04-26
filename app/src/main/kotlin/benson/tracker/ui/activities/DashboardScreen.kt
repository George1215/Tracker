package benson.tracker.ui.activities

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.location.Location
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.app.NotificationCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import benson.tracker.R
import benson.tracker.common.utils.PermissionUtils
import benson.tracker.common.utils.Toast
import benson.tracker.common.utils.isPermissionGranted
import benson.tracker.common.utils.requestPermission
import benson.tracker.domain.model.TrackedUser
import benson.tracker.ui.adapters.TrackedUsersAdapter
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.mikhaellopez.circularimageview.CircularImageView
import kotlinx.android.synthetic.main.screen_dashboard.*
import java.util.*
import kotlin.collections.ArrayList


class DashboardScreen : AppCompatActivity(),
        OnMapReadyCallback,
        GoogleMap.OnMyLocationButtonClickListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        ActivityCompat.OnRequestPermissionsResultCallback {

    val LOCATION_PERMISSION_REQUEST_CODE = 1

    var KEY_REQUESTING_LOCATION_UPDATES = "requesting-location-updates"
    val KEY_LOCATION = "location"
    val KEY_LAST_UPDATED_TIME_STRING = "last-updated-time-string"

    lateinit var apiClient: GoogleApiClient
    lateinit var locationRequest: LocationRequest
    lateinit var locationSettings: LocationSettingsRequest

    lateinit var auth: FirebaseAuth
    lateinit var authListener: FirebaseAuth.AuthStateListener
    lateinit var gMap: GoogleMap

    var actualLocation: Location? = null
    var requestingLocation: Boolean? = null

    var permissionDenied: Boolean = false

    var actualUserName: String = "username"
    var actualUserPin = 0

    val trackedUsers: ArrayList<TrackedUser> = ArrayList()

    var markers = HashMap<String, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_dashboard)

        auth = FirebaseAuth.getInstance()
        authListener = FirebaseAuth.AuthStateListener { }

        val supportMap: SupportMapFragment =
                supportFragmentManager.findFragmentById(R.id.dashboard_map) as SupportMapFragment

        supportMap.getMapAsync(this)

        requestingLocation = true

        updateWithBundle(savedInstanceState)
        setupApiClient()
        createLocationRequest()
        buildLocationSettingsRequest()

        // setup UI
        val database = FirebaseDatabase.getInstance()
        val databaseRef = database.reference

        // Username
        databaseRef.child("username").orderByValue().equalTo(auth.currentUser?.email).addValueEventListener(object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError?) {

            }

            override fun onDataChange(snapshot: DataSnapshot?) {
                user_profile_username.text = "Name: ${snapshot?.value.toString().split("=")[0].split("{")[1]}"
                actualUserName = snapshot?.value.toString().split("=")[0].split("{")[1]
                setupTracker(actualUserName)
            }
        })

        databaseRef.child("pin").orderByValue().equalTo(auth.currentUser?.email).addValueEventListener(object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError?) {

            }

            override fun onDataChange(snapshot: DataSnapshot?) {
                user_profile_pin.text = "Pin: ${snapshot?.value.toString().split("=")[0].split("{")[1]}"
                actualUserPin = snapshot?.value.toString().split("=")[0].split("{")[1].toInt()
            }
        })

        button_add_user.setOnClickListener {
            startActivity(Intent(this, TrackUserScreen::class.java))
            finish()
        }

        button_logout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, HomeScreen::class.java))
            finish()
        }
    }

    fun setupTracker(name: String) {
        val databaseRef = FirebaseDatabase.getInstance().reference

        // Database setup
        databaseRef.child("tracking").child(name).addValueEventListener(object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError?) {

            }

            override fun onDataChange(snapshot: DataSnapshot?) {
                val td: HashMap<*, *>? = snapshot?.value as HashMap<*, *>?
                val values: MutableCollection<out Any>? = td?.values

                if (values.toString() == "null") {
                    text_tracking.visibility = View.VISIBLE
                    list_tracked_users.adapter = null
                    list_tracked_users.visibility = View.GONE

                    gMap.clear()

                } else {
                    if (values?.isNotEmpty()!!) {

                        val trackedUsersAdapter: TrackedUsersAdapter = TrackedUsersAdapter(
                                this@DashboardScreen,
                                R.layout.tracked_users_row,
                                trackedUsers,
                                "tracking"
                        )

                        trackedUsersAdapter.clear()
                        trackedUsers.clear()

                        for (i in values) {
                            databaseRef.child("username").orderByValue().equalTo(i.toString()).addValueEventListener(object : ValueEventListener {
                                override fun onCancelled(de: DatabaseError?) {

                                }

                                override fun onDataChange(snapshot: DataSnapshot?) {
                                    trackedUsers.add(TrackedUser(snapshot?.value.toString().split('=')[0].split('{')[1]))
                                    trackedUsersAdapter.notifyDataSetChanged()

                                    // clear
                                    gMap.clear()
                                    for (username in trackedUsers) {

                                        databaseRef
                                                .child("coords")
                                                .child("user_latitude")
                                                .child(username.username).addValueEventListener(object : ValueEventListener {

                                            override fun onCancelled(de: DatabaseError?) {

                                            }

                                            override fun onDataChange(snap: DataSnapshot?) {
                                                val latitude = snap?.value
                                                if (latitude != null) {
                                                    databaseRef
                                                            .child("coords")
                                                            .child("user_longitude")
                                                            .child(username.username).addValueEventListener(object : ValueEventListener {
                                                        override fun onCancelled(de: DatabaseError?) {

                                                        }

                                                        override fun onDataChange(snap: DataSnapshot?) {
                                                            val longitude = snap?.value

                                                            val mk = markers[username.username]
                                                            mk?.remove()

                                                            val mkr: Marker =
                                                                    gMap.addMarker(
                                                                            MarkerOptions()
                                                                                    .title(username.username)
                                                                                    .position(LatLng(latitude as Double, longitude as Double))
                                                                                    .icon(BitmapDescriptorFactory.fromBitmap(getMarkerBitmapFromView(R.drawable.default_avatar)))
                                                                    )

                                                            markers.put(username.username, mkr)
                                                        }
                                                    })
                                                }
                                            }
                                        })
                                    }
                                }
                            })
                        }

                        list_tracked_users.adapter = trackedUsersAdapter
                        list_tracked_users.setOnItemClickListener { parent, view, position, id ->
                            startActivity(Intent(
                                    this@DashboardScreen,
                                    TrackedUserScreen::class.java).putExtra("tracked_user_name",
                                    trackedUsers[position].username)
                            )
                        }
                        list_tracked_users.visibility = View.VISIBLE
                        text_tracking.visibility = View.GONE
                    }
                }
            }
        })

        databaseRef.child("trackedBy").child(name).addValueEventListener(object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError?) {

            }

            override fun onDataChange(snapshot: DataSnapshot?) {
                val td: HashMap<*, *>? = snapshot?.value as HashMap<*, *>?
                val values: MutableCollection<out Any>? = td?.values

                if (values.toString() == "null") {
                    text_whos_tracking.visibility = View.VISIBLE
                    list_tracking_me.adapter = null
                    list_tracking_me.visibility = View.GONE

                } else {
                    if (values?.isNotEmpty()!!) {
                        val trackedBy: ArrayList<TrackedUser> = ArrayList()
                        val trackedUsersAdapter: TrackedUsersAdapter = TrackedUsersAdapter(
                                this@DashboardScreen,
                                R.layout.tracked_users_row,
                                trackedBy,
                                "trackedBy"
                        )

                        trackedBy.clear()
                        trackedUsersAdapter.clear()

                        for (i in values) {
                            databaseRef.child("username").orderByValue().equalTo(i.toString()).addValueEventListener(object : ValueEventListener {
                                override fun onCancelled(de: DatabaseError?) {

                                }

                                override fun onDataChange(snapshot: DataSnapshot?) {
                                    trackedBy.add(TrackedUser(snapshot?.value.toString().split('=')[0].split('{')[1]))
                                    trackedUsersAdapter.notifyDataSetChanged()
                                }
                            })
                        }
                        list_tracking_me.adapter = trackedUsersAdapter
                        list_tracking_me.visibility = View.VISIBLE
                        text_whos_tracking.visibility = View.GONE
                    }
                }
            }
        })
    }

    fun notifyNewUser(isNew: Boolean) {
        if (isNew) {
            val notificationCompat =
                    NotificationCompat.Builder(this@DashboardScreen)
                            .setSmallIcon(R.drawable.ic_fiber_new_black_24dp)
                            .setContentTitle("Tracker")
                            .setContentText("There's a new user tracking you!")
                            .setAutoCancel(true)


            val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            notificationManager.notify(0, notificationCompat.build())
        }
    }

    fun updateWithBundle(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            if (savedInstanceState.keySet().contains(KEY_REQUESTING_LOCATION_UPDATES))
                requestingLocation = savedInstanceState.getBoolean(KEY_REQUESTING_LOCATION_UPDATES)

            if (savedInstanceState.keySet().contains(KEY_LOCATION))
                actualLocation = savedInstanceState.getParcelable(KEY_LOCATION)
        }
    }

    @Synchronized fun setupApiClient() {
        apiClient = GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build()
    }

    fun createLocationRequest() {
        locationRequest = LocationRequest()

        locationRequest.interval = 1000
        locationRequest.fastestInterval = 500
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    fun buildLocationSettingsRequest() {
        val builder = LocationSettingsRequest.Builder()
        builder.addLocationRequest(locationRequest)
        locationSettings = builder.build()
    }

    fun startLocationUpdates() {
        LocationServices.SettingsApi.checkLocationSettings(
                apiClient,
                locationSettings
        ).setResultCallback { locationSettingsResult ->
            val status = locationSettingsResult.status
            when (status.statusCode) {
                LocationSettingsStatusCodes.SUCCESS -> {
                    Log.i("LOCATION", "All location settings are satisfied.")
                    LocationServices.FusedLocationApi.requestLocationUpdates(
                            apiClient, locationRequest, this@DashboardScreen)
                }
                LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                    Log.i("LOCATION", "Location settings are not satisfied. Attempting to upgrade " + "location settings ")
                    try {
                        // Show the dialog by calling startResolutionForResult(), and check the
                        // result in onActivityResult().
                        status.startResolutionForResult(this@DashboardScreen, 0x1)
                    } catch (e: IntentSender.SendIntentException) {
                        Log.i("LOCATION", "PendingIntent unable to execute request.")
                    }
                }
                LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                    val errorMessage = "Location settings are inadequate, and cannot be " + "fixed here. Fix in Settings."
                    Log.e("LOCATION", errorMessage)
                    errorMessage.Toast(this@DashboardScreen)
                    requestingLocation = false
                }
            }
            saveLoctData()
        }
    }

    fun saveLoctData() {
        val mLongitude = actualLocation?.longitude ?: 0.0
        val mLatitude = actualLocation?.latitude ?: 0.0

        if (mLongitude != 0.0 && mLatitude != 0.0) {

            val database = FirebaseDatabase.getInstance()
            val databaseRef = database.reference

            if (actualUserName != "username") {
                databaseRef.child("coords")
                        .child("user_longitude")
                        .child(actualUserName)
                        .setValue(mLongitude).addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d("Location", "Actual Longitude saved sucessfully!")
                    } else {
                        Log.d("Location", "Actual longitude has not been saved!")
                    }
                }

                databaseRef.child("coords")
                        .child("user_latitude")
                        .child(actualUserName)
                        .setValue(mLatitude).addOnCompleteListener {
                    if (it.isSuccessful) {
                        Log.d("Location", "Actual latitude saved sucessfully!")
                    } else {
                        Log.d("Location", "Actual latitude has not been saved!")
                    }
                }
            }
        }
    }

    fun stopLocationUpdates() {
        LocationServices.FusedLocationApi
                .removeLocationUpdates(
                        apiClient, this
                ).setResultCallback { requestingLocation = false }
    }

    override fun onConnected(p0: Bundle?) {
        if (actualLocation == null) {
            actualLocation = LocationServices.FusedLocationApi.getLastLocation(apiClient)
            saveLoctData()
        }
        if (requestingLocation == true) {
            Log.i("Location", "in onConnected(), starting location updates")
            startLocationUpdates()
        }
    }

    override fun onConnectionSuspended(p0: Int) {

    }

    override fun onConnectionFailed(p0: ConnectionResult) {

    }

    override fun onLocationChanged(location: Location?) {
        actualLocation = location
        saveLoctData()
    }

    override fun onMapReady(map: GoogleMap?) {
        if (map != null) {
            gMap = map
            gMap.setOnMyLocationButtonClickListener(this)
            addMyLocation()

            startLocationUpdates()
        }
    }

    fun addMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(
                    this,
                    LOCATION_PERMISSION_REQUEST_CODE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    true
            )
        } else {
            gMap.isMyLocationEnabled = true
        }
    }

    override fun onMyLocationButtonClick(): Boolean {
        return false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE)
            return

        if (isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION))
            addMyLocation()
        else
            permissionDenied = true

    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        if (permissionDenied) {
            showMissingPermissionError()
            permissionDenied = false
        }
    }

    private fun showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(supportFragmentManager, "dialog")
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authListener)
        apiClient.connect()
    }

    override fun onResume() {
        super.onResume()
        if (apiClient.isConnected && requestingLocation == true) {
            startLocationUpdates()
        }
        saveLoctData()
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authListener)

        apiClient.disconnect()
    }

    override fun onPause() {
        super.onPause()
        if (apiClient.isConnected)
            stopLocationUpdates()
    }

    private fun getMarkerBitmapFromView(@DrawableRes resId: Int): Bitmap {
        val customMarkerView = (getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater).inflate(R.layout.marker_header, null)
        val markerImageView = customMarkerView.findViewById(R.id.marker_profile_photo) as CircularImageView
        markerImageView.setImageResource(resId)
        customMarkerView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        customMarkerView.layout(0, 0, customMarkerView.measuredWidth, customMarkerView.measuredHeight)
        customMarkerView.buildDrawingCache()
        val returnedBitmap = Bitmap.createBitmap(customMarkerView.measuredWidth, customMarkerView.measuredHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        canvas.drawColor(Color.WHITE, PorterDuff.Mode.SRC_IN)
        customMarkerView.background?.draw(canvas)
        customMarkerView.draw(canvas)
        return returnedBitmap
    }

    @Synchronized fun randomColor(): Int =
            0xff000000.toInt() + 256 * 256 *
                    Random().nextInt(256) + 256 *
                    Random().nextInt(256) + Random().nextInt(256)

}