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
  val credentials: ExamplePlayerCredentials
) {

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
      var credentials = ExamplePlayerCredentials.None as ExamplePlayerCredentials
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
                credentials = ExamplePlayerCredentials.Basic(
                  userName = parser.getAttributeValue(null, "userName"),
                  password = parser.getAttributeValue(null, "password")
                )
              }

              "Feedbooks" -> {
                credentials = ExamplePlayerCredentials.Feedbooks(
                  userName = parser.getAttributeValue(null, "userName"),
                  password = parser.getAttributeValue(null, "password"),
                  bearerTokenSecret = parser.getAttributeValue(null, "bearerTokenSecret"),
                  issuerURL = parser.getAttributeValue(null, "issuerURL")
                )
              }

              "AuthenticationNone" -> {
                credentials = ExamplePlayerCredentials.None
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
                    credentials = credentials
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