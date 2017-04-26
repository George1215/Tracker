package benson.tracker.ui.activities

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import benson.tracker.R
import com.google.firebase.auth.FirebaseAuth

class SplashScreen : AppCompatActivity() {

    lateinit var auth: FirebaseAuth
    lateinit var authListener: FirebaseAuth.AuthStateListener

    var isSignedIn: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_splash)

        auth = FirebaseAuth.getInstance()
        authListener = FirebaseAuth.AuthStateListener {
            isSignedIn = auth.currentUser != null
            when (isSignedIn) {
                true ->
                    startActivity(Intent(this, DashboardScreen::class.java))

                false ->
                    startActivity(Intent(this, HomeScreen::class.java))

            }
            finish()
        }
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
