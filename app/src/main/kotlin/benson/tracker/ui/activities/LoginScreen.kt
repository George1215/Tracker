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
import kotlinx.android.synthetic.main.screen_login.*

class LoginScreen : AppCompatActivity() {

    lateinit var auth: FirebaseAuth
    lateinit var authListener: FirebaseAuth.AuthStateListener

    var isSignedIn: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_login)

        setSupportActionBar(app_bar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        app_bar.setNavigationOnClickListener { finish() }

        auth = FirebaseAuth.getInstance()
        authListener = FirebaseAuth.AuthStateListener {
            isSignedIn = auth.currentUser != null

            if (isSignedIn)
                startActivity(Intent(this, DashboardScreen::class.java))
        }

        val databaseRef = FirebaseDatabase.getInstance().reference

        button_login.setOnClickListener {
            val userLogin = input_email_username.text.toString()
            val userPassw = input_password.text.toString()

            if (userLogin.isNullOrEmpty() || userPassw.isNullOrEmpty()) {
                "Fill the form before you go".Toast(applicationContext)
            } else {
                if (userLogin.isValidEmail()) {
                    performLogin(userLogin, userPassw)
                } else {
                    databaseRef.child("username").child(userLogin).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onCancelled(de: DatabaseError?) {
                            de.toString().Toast(applicationContext)
                        }

                        override fun onDataChange(da: DataSnapshot?) {
                            if (da!!.exists()) {
                                val email = da.value.toString()
                                performLogin(email, userPassw)
                            } else {
                                "This user doesn't exists. Try again.".Toast(applicationContext)
                            }
                        }
                    })
                }
            }
        }
    }

    fun performLogin(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener {
            when (it.isSuccessful) {
                true -> {
                    startActivity(Intent(this@LoginScreen, DashboardScreen::class.java))
                    finish()
                }
                false ->
                    it.exception?.message ?: "Failed to login".Toast(applicationContext)
            }
        }
    }
}