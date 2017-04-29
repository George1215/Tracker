package benson.tracker.ui.activities

import android.Manifest
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import benson.tracker.R
import benson.tracker.common.utils.*
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

    var disW = 0.0
    var mmW = 0.0

    // it's all about timing
    var secsIdle = 0L
    var idle = 0L
    var idMt = 0L
    var secsIdMt = 0L

    var secsMovin = 0L
    var movin = 0L
    var mmMt = 0L
    var secsMmt = 0L

    var timer: Timer? = null
    val handler: Handler = Handler()

    lateinit var task: TimerTask

    var isUserMoving: Boolean = false
    var startedTimer: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_dashboard)

        auth = FirebaseAuth.getInstance()
        authListener = FirebaseAuth.AuthStateListener {
            if (auth.currentUser == null) {
                startActivity(Intent(this, HomeScreen::class.java))
                finish()
            }
        }

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
                if (snapshot?.exists()!!) {
                    val n = snapshot.value.toString().split("=")[0].split("{")[1]

                    user_profile_username.text = "Name: $n"
                    actualUserName = n

                    if (actualUserName == "username") {
                        signout()
                    } else {
                        dateOfTheDay()
                        setupTracker(actualUserName)
                    }
                } else {

                }
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
                                                                                    .icon(BitmapDescriptorFactory.fromBitmap(applicationContext.getMarkerBitmapFromView(R.drawable.default_avatar)))
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
                        list_tracked_users.setOnItemClickListener { _, _, position, _ ->
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

    fun dateOfTheDay() {
        val databaseRef = FirebaseDatabase.getInstance().reference

        val datetime: Calendar = Calendar.getInstance()

        val yy: Int = datetime.get(Calendar.YEAR)
        val mm: Int = datetime.get(Calendar.MONTH) + 1
        val dd: Int = datetime.get(Calendar.DAY_OF_MONTH)

        var mDate: String = "%d-%d-%d".format(yy, mm, dd)

        databaseRef.child("gps").child(actualUserName).child("date").addValueEventListener(object : ValueEventListener {
            override fun onCancelled(de: DatabaseError?) {

            }

            override fun onDataChange(ds: DataSnapshot?) {
                if (ds?.exists()!!) {
                    val userDate = ds.value as String

                    if (userDate.split("-")[1].toInt() != mm) {
                        databaseRef.child("gps").child(actualUserName.plus("_mm")).child("distance_walked").setValue(null)
                        databaseRef.child("gps").child(actualUserName.plus("_mm")).child("idle_time").setValue(null)
                        databaseRef.child("gps").child(actualUserName.plus("_mm")).child("movin_time").setValue(null)
                        databaseRef.child("gps").child(actualUserName.plus("_mm")).child("average_speed").setValue(null)

                        databaseRef.child("gps").child(actualUserName).child("date").setValue(null)
                        databaseRef.child("gps").child(actualUserName).child("distance_walked").setValue(null)
                        databaseRef.child("gps").child(actualUserName).child("idle_time").setValue(null)
                        databaseRef.child("gps").child(actualUserName).child("movin_time").setValue(null)
                        databaseRef.child("gps").child(actualUserName).child("average_speed").setValue(null)

                        databaseRef.child("gps").child(actualUserName.plus("_old")).child("date").setValue(null)
                        databaseRef.child("gps").child(actualUserName.plus("_old")).child("distance_walked").setValue(null)
                        databaseRef.child("gps").child(actualUserName.plus("_old")).child("idle_time").setValue(null)
                        databaseRef.child("gps").child(actualUserName.plus("_old")).child("movin_time").setValue(null)
                        databaseRef.child("gps").child(actualUserName.plus("_old")).child("average_speed").setValue(null)
                    }

                    if (userDate.split("-")[2].toInt() < dd && mDate.split("-")[1].toInt() == mm) {
                        // date
                        databaseRef.child("gps").child(actualUserName.plus("_old")).child("date").setValue(userDate)

                        databaseRef.child("gps").child(actualUserName).child("distance_walked").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onCancelled(err: DatabaseError?) {

                            }

                            override fun onDataChange(ds: DataSnapshot?) {
                                if (ds?.exists()!!) {
                                    databaseRef.child("gps").child(actualUserName.plus("_old")).child("distance_walked").setValue(ds.value)
                                    databaseRef.child("gps").child(actualUserName).child("distance_walked").setValue(null)
                                }
                            }
                        })

                        databaseRef.child("gps").child(actualUserName).child("idle_time").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onCancelled(err: DatabaseError?) {

                            }

                            override fun onDataChange(ds: DataSnapshot?) {
                                if (ds?.exists()!!) {
                                    databaseRef.child("gps").child(actualUserName.plus("_old")).child("idle_time").setValue(ds.value)
                                    databaseRef.child("gps").child(actualUserName).child("idle_time").setValue(null)
                                }
                            }
                        })

                        databaseRef.child("gps").child(actualUserName).child("movin_time").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onCancelled(err: DatabaseError?) {

                            }

                            override fun onDataChange(ds: DataSnapshot?) {
                                if (ds?.exists()!!) {
                                    databaseRef.child("gps").child(actualUserName.plus("_old")).child("movin_time").setValue(ds.value)
                                    databaseRef.child("gps").child(actualUserName).child("movin_time").setValue(null)
                                }
                            }
                        })

                        databaseRef.child("gps").child(actualUserName).child("average_speed").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onCancelled(err: DatabaseError?) {

                            }

                            override fun onDataChange(ds: DataSnapshot?) {
                                if (ds?.exists()!!) {
                                    databaseRef.child("gps").child(actualUserName.plus("_old")).child("average_speed").setValue(ds.value)
                                    databaseRef.child("gps").child(actualUserName).child("average_speed").setValue(null)
                                }
                            }
                        })
                        databaseRef.child("gps").child(actualUserName).child("date").setValue(null)
                    }
                } else {
                    databaseRef.child("gps").child(actualUserName).child("date").setValue(mDate)
                }
            }
        })

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
        if (actualLocation != null && location != null && actualUserName != "username") {

            val timeDif: Int = (location.time.toInt() - actualLocation?.time!!.toInt()) / 1000
            val distance: Double = actualLocation?.distanceTo(location)?.toDouble() ?: 0.0
            val speed: Double = distance / timeDif

            val databaseRef = FirebaseDatabase.getInstance().reference

            isUserMoving = distance > 0.0

            if (isUserMoving) {
                databaseRef.child("gps").child(actualUserName).child("distance_walked").addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(err: DatabaseError?) {

                    }

                    override fun onDataChange(snap: DataSnapshot?) {
                        if (snap?.exists()!!) {
                            disW = snap.value as Double
                        } else {
                            val finalDir: Double = distance / 1000
                            databaseRef.child("gps").child(actualUserName).child("distance_walked").setValue(finalDir)
                        }
                    }

                })

                databaseRef.child("gps").child(actualUserName.plus("_mm")).child("distance_walked").addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(p0: DatabaseError?) {

                    }

                    override fun onDataChange(p0: DataSnapshot?) {
                        if (p0?.exists()!!) {
                            mmW = p0.value as Double
                        } else {
                            val finalDir: Double = distance / 1000
                            databaseRef.child("gps").child(actualUserName.plus("_mm")).child("distance_walked").setValue(finalDir)
                        }
                    }
                })

                if (disW != 0.0) {
                    databaseRef.child("gps").child(actualUserName).child("distance_walked").setValue(distance / 1000 + disW)
                    disW = 0.0
                }

                if (mmW != 0.0) {
                    databaseRef.child("gps").child(actualUserName.plus("_mm")).child("distance_walked").setValue(distance / 1000 + mmW)
                    mmW = 0.0
                }

                databaseRef.child("gps").child(actualUserName).child("average_speed").addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(err: DatabaseError?) {

                    }

                    override fun onDataChange(snap: DataSnapshot?) {
                        if (snap?.exists()!!) {
                            val mSpeed: Double = snap.value as Double

                            if (speed > 0.0 && speed > mSpeed) {
                                databaseRef.child("gps").child(actualUserName).child("average_speed").setValue(speed)
                            }
                        } else {
                            if (speed > 0.0) {
                                databaseRef.child("gps").child(actualUserName).child("average_speed").setValue(speed)
                            }
                        }
                    }
                })

                databaseRef.child("gps").child(actualUserName.plus("_mm")).child("average_speed").addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(err: DatabaseError?) {

                    }

                    override fun onDataChange(snap: DataSnapshot?) {
                        if (snap?.exists()!!) {
                            val mSpeed: Double = snap.value as Double

                            if (speed > 0.0 && speed > mSpeed) {
                                databaseRef.child("gps").child(actualUserName.plus("_mm")).child("average_speed").setValue(speed)
                            }
                        } else {
                            if (speed > 0.0) {
                                databaseRef.child("gps").child(actualUserName.plus("_mm")).child("average_speed").setValue(speed)
                            }
                        }
                    }
                })
            }

            actualLocation = location
            saveLoctData()
        } else {
            isUserMoving = false
        }
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

        startTimer()
        saveLoctData()
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authListener)

        apiClient.disconnect()

        stopTimer()
        startedTimer = false
    }

    override fun onPause() {
        super.onPause()
        if (apiClient.isConnected)
            stopLocationUpdates()

        stopTimer()
        startedTimer = false
    }

    var hd = Handler()
    var runnable: Runnable = Runnable {
        if (isUserMoving) {
            secsMovin++
            secsMmt++
            addMovinTimer(secsMovin, secsMmt)
        } else {
            secsIdle++
            secsIdMt++
            addIdleTimer(secsIdle, secsIdMt)
        }
    }

    fun startTimer() {
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                hd.post(runnable)
                startedTimer = true
            }
        }, 3000, 1000)
    }


    fun stopTimer() {
        if (timer != null) {
            hd.removeCallbacks(runnable)
            timer?.cancel()
            timer?.purge()
            timer = null
        }
    }

    fun signout() {
        auth.signOut()
        finish()
        startActivity(Intent(this@DashboardScreen, HomeScreen::class.java))
    }

    fun addIdleTimer(v: Long, m: Long) {
        val ref = FirebaseDatabase.getInstance().reference

        if (actualUserName != "username") {
            ref.child("gps").child(actualUserName).child("idle_time").addValueEventListener(object : ValueEventListener {
                override fun onCancelled(derr: DatabaseError?) {

                }

                override fun onDataChange(snap: DataSnapshot?) {
                    if (snap?.exists()!!) {
                        idle = snap.value as Long
                    } else {
                        ref.child("gps").child(actualUserName).child("idle_time").setValue(v)
                        Log.d("AA", "o.o")
                    }
                }
            })

            if (idle != 0L) {
                ref.child("gps").child(actualUserName).child("idle_time").setValue(v + idle)
                secsIdle = 0L
            }

            ref.child("gps").child(actualUserName.plus("_mm")).child("idle_time").addValueEventListener(object : ValueEventListener {
                override fun onCancelled(derr: DatabaseError?) {

                }

                override fun onDataChange(snap: DataSnapshot?) {
                    if (snap?.exists()!!) {
                        idMt = snap.value as Long
                    } else {
                        ref.child("gps").child(actualUserName.plus("_mm")).child("idle_time").setValue(m)
                    }
                }
            })

            if (idMt != 0L) {
                ref.child("gps").child(actualUserName.plus("_mm")).child("idle_time").setValue(m + idMt)
                secsIdMt = 0L
            }
        }
    }

    fun addMovinTimer(v: Long, m: Long) {
        val ref = FirebaseDatabase.getInstance().reference

        ref.child("gps").child(actualUserName).child("movin_time").addValueEventListener(object : ValueEventListener {
            override fun onCancelled(derr: DatabaseError?) {

            }

            override fun onDataChange(snap: DataSnapshot?) {
                if (snap?.exists()!!) {
                    movin = snap.value as Long
                } else {
                    ref.child("gps").child(actualUserName).child("movin_time").setValue(v)
                }
            }
        })

        if (movin != 0L) {
            ref.child("gps").child(actualUserName).child("movin_time").setValue(v + movin)
            secsMovin = 0L
        }

        ref.child("gps").child(actualUserName.plus("_mm")).child("movin_time").addValueEventListener(object : ValueEventListener {
            override fun onCancelled(derr: DatabaseError?) {

            }

            override fun onDataChange(snap: DataSnapshot?) {
                if (snap?.exists()!!) {
                    mmMt = snap.value as Long
                } else {
                    ref.child("gps").child(actualUserName.plus("_mm")).child("movin_time").setValue(m)
                }
            }
        })

        if (mmMt != 0L) {
            ref.child("gps").child(actualUserName.plus("_mm")).child("movin_time").setValue(m + mmMt)
            secsMmt = 0L
        }
    }
}