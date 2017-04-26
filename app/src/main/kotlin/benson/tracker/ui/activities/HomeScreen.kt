package benson.tracker.ui.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import benson.tracker.R
import com.google.firebase.auth.FirebaseAuth
import kotlinx.android.synthetic.main.screen_home.*

class HomeScreen : AppCompatActivity() {

    lateinit var auth: FirebaseAuth
    lateinit var authListener: FirebaseAuth.AuthStateListener

    var isSignedIn: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_home)

        auth = FirebaseAuth.getInstance()
        authListener = FirebaseAuth.AuthStateListener {
            isSignedIn = auth.currentUser != null
            if (isSignedIn) {
                startActivity(Intent(this, DashboardScreen::class.java))
                finish()
            }
        }

        setSupportActionBar(app_bar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        app_bar.setNavigationOnClickListener { finish() }

        button_signin.setOnClickListener { startActivity(Intent(this, LoginScreen::class.java)) }
        button_signup.setOnClickListener { startActivity(Intent(this, SignupScreen::class.java)) }

    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authListener)
    }
}
