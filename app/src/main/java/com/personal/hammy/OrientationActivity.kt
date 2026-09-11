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

        findViewById<Button>(R.id.cardStraight).setOnClickListener {
            pick(Prefs.Orientation.STRAIGHT)
        }
        findViewById<Button>(R.id.cardGay).setOnClickListener {
            pick(Prefs.Orientation.GAY)
        }
        findViewById<Button>(R.id.cardTrans).setOnClickListener {
            pick(Prefs.Orientation.TRANS)
        }
    }
}
