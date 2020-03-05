package org.librarysimplified.audiobook.demo.main_ui

import android.content.Context
import org.xmlpull.v1.XmlPullParser
import java.net.URI

/**
 * Bundled presets for testing various audio books.
 */

data class ExamplePreset(
  val name: String,
  val uri: URI,
  val authentication: Authentication
) {

  enum class Authentication {
    NONE,
    BASIC
  }

  companion object {

    /**
     * Parse bundled repositories from XML resources.
     */

    fun fromXMLResources(context: Context): List<ExamplePreset> {
      return loadFrom(context.resources.getXml(R.xml.presets))
    }

    /**
     * Load presets from the given XML parser.
     */

    fun loadFrom(
      parser: XmlPullParser
    ): List<ExamplePreset> {
      var name = ""
      var location = ""
      var auth = Authentication.NONE
      val presets = mutableListOf<ExamplePreset>()

      while (true) {
        when (parser.next()) {
          XmlPullParser.END_DOCUMENT ->
            return presets.toList()

          XmlPullParser.START_TAG ->
            when (parser.name) {
              "Presets" -> {

              }
              "Preset" -> {
                name = parser.getAttributeValue(null, "name")
                location = parser.getAttributeValue(null, "location")
              }
              "AuthenticationBasic" -> {
                auth = Authentication.BASIC
              }
              "AuthenticationNone" -> {
                auth = Authentication.NONE
              }
              else -> {

              }
            }

          XmlPullParser.END_TAG -> {
            when (parser.name) {
              "Presets" -> Unit
              "Preset" -> {
                presets.add(
                  ExamplePreset(
                    name = name,
                    uri = URI.create(location),
                    authentication = auth
                  )
                )
              }
              else -> {

              }
            }
          }

          else -> Unit
        }
      }
    }
  }
}