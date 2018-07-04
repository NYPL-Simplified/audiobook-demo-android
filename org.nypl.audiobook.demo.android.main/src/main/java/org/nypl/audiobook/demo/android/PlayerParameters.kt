package org.nypl.audiobook.demo.android

import java.io.Serializable

/**
 * The parameters used to fetch a remote manifest.
 */

data class PlayerParameters(
  val user: String,
  val password: String,
  val fetchURI: String) : Serializable
