package com.example.wififtm

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.wififtm.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val ftmFragment = FtmFragment()
    private val beaconFragment = BeaconFragment()
    private var activeFragment: Fragment = ftmFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Add both fragments; show FTM first
        supportFragmentManager.beginTransaction()
            .add(R.id.fragmentContainer, beaconFragment, "beacon").hide(beaconFragment)
            .add(R.id.fragmentContainer, ftmFragment, "ftm")
            .commit()

        binding.bottomNav.setOnItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_ftm -> ftmFragment
                R.id.nav_beacon -> beaconFragment
                else -> return@setOnItemSelectedListener false
            }
            if (target !== activeFragment) {
                supportFragmentManager.beginTransaction()
                    .hide(activeFragment)
                    .show(target)
                    .commit()
                activeFragment = target
            }
            true
        }
    }
}
