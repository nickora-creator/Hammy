package com.personal.hammy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AgeGateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Prefs.isAgeConfirmed(this) && Prefs.isSetupDone(this)) {
            startActivity(Intent(this, BrowseActivity::class.java))
            finish()
            return
        }
        if (Prefs.isAgeConfirmed(this)) {
            startActivity(Intent(this, OrientationActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_age_gate)

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            Prefs.setAgeConfirmed(this, true)
            startActivity(Intent(this, OrientationActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.btnExit).setOnClickListener {
            finishAffinity()
        }
    }
}
