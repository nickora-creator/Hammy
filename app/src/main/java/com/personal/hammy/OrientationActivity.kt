package com.personal.hammy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class OrientationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_orientation)

        fun pick(orientation: Prefs.Orientation) {
            Prefs.setOrientation(this, orientation)
            startActivity(Intent(this, CategoryActivity::class.java))
            finish()
        }

        val straight = findViewById<Button>(R.id.cardStraight)
        val gay = findViewById<Button>(R.id.cardGay)
        val trans = findViewById<Button>(R.id.cardTrans)
        TvFocus.attach(straight, scale = 1.12f)
        TvFocus.attach(gay, scale = 1.12f)
        TvFocus.attach(trans, scale = 1.12f)

        straight.setOnClickListener { pick(Prefs.Orientation.STRAIGHT) }
        gay.setOnClickListener { pick(Prefs.Orientation.GAY) }
        trans.setOnClickListener { pick(Prefs.Orientation.TRANS) }
    }
}
