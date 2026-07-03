package com.psike.pantrypilot

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val view = TextView(this).apply {
            text = "PantryPilot skeleton works"
            textSize = 24f
            setPadding(32, 64, 32, 32)
        }

        setContentView(view)
    }
}
