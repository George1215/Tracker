package benson.tracker.ui.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import benson.tracker.R
import kotlinx.android.synthetic.main.screen_tracked_user.*

class TrackedUserScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_tracked_user)

        setSupportActionBar(app_toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        app_toolbar.setNavigationOnClickListener { finish() }

        val trackedUserName: String? = intent.extras.getString("tracked_user_name")
        app_toolbar.title = trackedUserName.plus("'s Report")
    }
}
