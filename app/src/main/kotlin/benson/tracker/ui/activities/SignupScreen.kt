package benson.tracker.ui.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import benson.tracker.R
import benson.tracker.common.utils.Toast
import benson.tracker.common.utils.isValidEmail
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.android.synthetic.main.screen_signup.*
import java.util.*

class SignupScreen : AppCompatActivity() {

    lateinit var auth: FirebaseAuth
    lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_signup)

        setSupportActionBar(app_bar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        app_bar.setNavigationOnClickListener { finish() }

        auth = FirebaseAuth.getInstance()

        database = FirebaseDatabase.getInstance()
        val databaseRef = database.reference

        button_signup.setOnClickListener {
            if (input_username.text.isNullOrEmpty() || input_username.text.length < 4 || !input_email.text.toString().isValidEmail()) {
                "Your' username or email is not valid. Try again!".Toast(applicationContext)
            } else {
                databaseRef.child("username").child(input_username.text.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(de: DatabaseError?) {
                        de.toString().Toast(applicationContext)
                    }

                    override fun onDataChange(ds: DataSnapshot?) {
                        if (ds!!.exists())
                            "This username already exists!".Toast(applicationContext)
                        else {
                            val username = input_username.text.toString()
                            val email = input_email.text.toString()
                            val pwd = input_password.text.toString()

                            auth.createUserWithEmailAndPassword(email, pwd).addOnCompleteListener {
                                if (it.isSuccessful) {
                                    databaseRef.child("username").child(username)
                                            .setValue(email).addOnCompleteListener {

                                        if (it.isSuccessful) {
                                            databaseRef.child("pin")
                                                    .child(createUserPin().toString())
                                                    .setValue(email).addOnCompleteListener {

                                                if (it.isSuccessful) {
                                                    "Your account has been created".Toast(applicationContext)
                                                    startActivity(Intent(this@SignupScreen, DashboardScreen::class.java))
                                                    finish()

                                                } else {
                                                    it.exception?.message ?: "An error has been occured"
                                                            .Toast(applicationContext)
                                                }

                                            }

                                        } else {
                                            it.exception?.message ?: "An error has been occured"
                                                    .Toast(applicationContext)
                                        }
                                    }
                                } else {
                                    it.exception?.message ?: "Failed to create your account"
                                            .Toast(applicationContext)
                                }
                            }
                        }
                    }
                })
            }
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    fun createUserPin(): Int {
        val rand: Random = Random()
        val value = rand.nextInt(1000000)

        val databaseRef = database.reference
        databaseRef.child("pin").child(value.toString()).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onCancelled(de: DatabaseError?) {

            }

            override fun onDataChange(ds: DataSnapshot?) {
                if (ds!!.exists())
                    createUserPin()
            }
        })
        return value
    }

}