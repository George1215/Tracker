package benson.tracker.ui.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import benson.tracker.R
import benson.tracker.common.utils.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.screen_track_user.*

class TrackUserScreen : AppCompatActivity() {

    lateinit var auth: FirebaseAuth

    var actualUserName: String? = null
    var actualUserPin = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_track_user)

        setSupportActionBar(app_bar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        app_bar.setNavigationOnClickListener {
            startActivity(Intent(this, DashboardScreen::class.java))
            finish()
        }

        auth = FirebaseAuth.getInstance()

        val database = FirebaseDatabase.getInstance()
        val databaseRef = database.reference

        databaseRef.child("username").orderByValue().equalTo(auth.currentUser?.email).addValueEventListener(object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError?) {

            }

            override fun onDataChange(snapshot: DataSnapshot?) {
                actualUserName = snapshot?.value.toString().split("=")[0].split("{")[1]
            }
        })

        databaseRef.child("pin").orderByValue().equalTo(auth.currentUser?.email).addValueEventListener(object : ValueEventListener {
            override fun onCancelled(databaseError: DatabaseError?) {

            }

            override fun onDataChange(snapshot: DataSnapshot?) {
                actualUserPin = snapshot?.value.toString().split("=")[0].split("{")[1].toInt()
            }
        })
        button_track_user.setOnClickListener {
            val name = input_add_user_name.text.toString()
            val pin = input_add_user_pin.text.toString()

            if (name.isNullOrEmpty() || pin.isNullOrEmpty()) {
                "Fill the form".Toast(this)
            } else if (pin.toInt() == actualUserPin) {
                "Can't add yourself".Toast(this)
            } else {
                databaseRef.child("username").child(name).addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError?) {

                    }

                    override fun onDataChange(snapshot: DataSnapshot?) {
                        if (!snapshot?.exists()!!)
                            "This user name doesn't exists".Toast(this@TrackUserScreen)
                        else {
                            val email = snapshot.value.toString()

                            databaseRef.child("tracking")
                                    .child(actualUserName)
                                    .child(name)
                                    .setValue(email).addOnCompleteListener {

                                databaseRef.child("trackedBy")
                                        .child(name)
                                        .child(actualUserName)
                                        .setValue(auth.currentUser?.email).addOnCompleteListener {

                                    if (it.isSuccessful) {
                                        "User $name has been added!".Toast(this@TrackUserScreen)
                                    }
                                }
                            }
                        }
                    }
                })
            }
        }
    }

}