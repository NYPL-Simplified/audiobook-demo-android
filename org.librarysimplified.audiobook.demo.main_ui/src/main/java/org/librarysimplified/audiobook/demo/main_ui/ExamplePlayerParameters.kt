package org.librarysimplified.audiobook.demo.main_ui

import java.io.Serializable

/**
 * The parameters used to fetch a remote manifest.
 */

data class ExamplePlayerParameters(
  val credentials: ExamplePlayerCredentials,
  val fetchURI: String
) : Serializable
