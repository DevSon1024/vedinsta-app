package com.devson.vedinsta

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.devson.vedinsta.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupContent()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "About VedInsta"
        }
    }

    private fun setupContent() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvVersion.text = "Version ${packageInfo.versionName}"
        } catch (e: PackageManager.NameNotFoundException) {
            binding.tvVersion.text = "Version Unknown"
        }
    }

    private fun setupClickListeners() {
        binding.btnGithub.setOnClickListener {
            openUrl("https://github.com/DevSon1024/vedinsta-app")
        }

        binding.btnReportIssue.setOnClickListener {
            openUrl("https://github.com/DevSon1024/vedinsta-app/issues")
        }

        binding.btnDeveloper.setOnClickListener {
            openUrl("https://github.com/DevSon1024")
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
