package foundation.icon.iconex.view

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import foundation.icon.iconex.R
import foundation.icon.iconex.view.ui.create.CreateFragment

class CreateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.create_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.container, CreateFragment.newInstance())
                    .commitNow()
        }
    }

}