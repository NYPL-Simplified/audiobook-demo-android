package org.librarysimplified.audiobook.demo.main_ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ExampleConfigurationActivity : AppCompatActivity() {

  private lateinit var authenticationSelected: String
  private lateinit var authBasic: String
  private lateinit var authentication: Spinner
  private lateinit var authenticationBasic: ViewGroup
  private lateinit var authenticationBasicPassword: TextView
  private lateinit var authenticationBasicUser: TextView
  private lateinit var authItems: Array<String>
  private lateinit var authNone: String
  private lateinit var location: TextView
  private lateinit var play: Button
  private lateinit var presets: Spinner

  override fun onCreate(state: Bundle?) {
    super.onCreate(state)

    this.setContentView(R.layout.example_config_screen)

    this.authNone =
      this.getString(R.string.exAuthNone)
    this.authBasic =
      this.getString(R.string.exAuthBasic)
    this.authItems =
      this.resources.getStringArray(R.array.exAuthenticationTypes)

    this.authenticationBasic =
      this.findViewById(R.id.exAuthenticationBasicParameters)
    this.authenticationBasicUser =
      this.authenticationBasic.findViewById(R.id.exAuthenticationBasicUser)
    this.authenticationBasicPassword =
      this.authenticationBasic.findViewById(R.id.exAuthenticationBasicPassword)
    this.authentication =
      this.findViewById(R.id.exAuthenticationSelection)
    this.authentication.adapter =
      ArrayAdapter.createFromResource(
        this, R.array.exAuthenticationTypes, android.R.layout.simple_list_item_1
      )
    this.play =
      this.findViewById(R.id.exPlay)

    this.location =
      this.findViewById(R.id.exLocation)
    this.presets =
      this.findViewById(R.id.exPresets)

    this.onSelectedAuthentication(this.authNone)
  }

  override fun onStart() {
    super.onStart()

    this.authentication.onItemSelectedListener =
      object : AdapterView.OnItemSelectedListener {
        override fun onNothingSelected(parent: AdapterView<*>?) {

        }

        override fun onItemSelected(
          parent: AdapterView<*>?,
          view: View?,
          position: Int,
          id: Long
        ) {
          this@ExampleConfigurationActivity.onSelectedAuthentication(
            this@ExampleConfigurationActivity.authentication.getItemAtPosition(position) as String
          )
        }
      }

    val presetList =
      ExamplePreset.fromXMLResources(this)
    val presetAdapter =
      ArrayAdapter(
        this,
        android.R.layout.simple_list_item_1,
        presetList.map { p -> p.name }.toTypedArray()
      )

    this.presets.adapter = presetAdapter
    this.presets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onNothingSelected(parent: AdapterView<*>?) {

      }

      override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
      ) {
        this@ExampleConfigurationActivity.onSelectedPreset(presetList[position])
      }
    }

    this.play.setOnClickListener {
      this.onSelectedPlay()
    }
  }

  private fun onSelectedPlay() {
    val credentials =
      when (this.authenticationSelected) {
        this.authBasic -> {
          ExamplePlayerCredentials.Basic(
            userName = this.authenticationBasicUser.text.toString(),
            password = this.authenticationBasicPassword.text.toString()
          )
        }
        this.authNone -> {
          ExamplePlayerCredentials.None
        }
        else -> {
          throw UnsupportedOperationException()
        }
      }

    val parameters =
      ExamplePlayerParameters(
        credentials = credentials,
        fetchURI = this.location.text.toString()
      )

    val args = Bundle()
    args.putSerializable(ExamplePlayerActivity.FETCH_PARAMETERS_ID, parameters)
    val intent = Intent(this, ExamplePlayerActivity::class.java)
    intent.putExtras(args)
    this.startActivity(intent)
  }

  private fun onSelectedAuthentication(authentication: String) {
    this.authenticationSelected = authentication
    this.authentication.setSelection(this.authItems.indexOf(authentication))

    return when (authentication) {
      this.authBasic -> {
        this.authenticationBasic.visibility = View.VISIBLE
      }
      this.authNone -> {
        this.authenticationBasic.visibility = View.GONE
      }
      else -> {
        throw UnsupportedOperationException()
      }
    }
  }

  private fun onSelectedPreset(preset: ExamplePreset) {
    this.location.text = preset.uri.toString()
    this.onSelectedAuthentication(
      when (preset.authentication) {
        ExamplePreset.Authentication.NONE -> this.authNone
        ExamplePreset.Authentication.BASIC -> this.authBasic
      }
    )
  }
}
