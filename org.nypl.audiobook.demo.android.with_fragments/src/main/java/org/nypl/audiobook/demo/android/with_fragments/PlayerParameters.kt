package org.nypl.audiobook.demo.android.with_fragments

import java.io.Serializable

/**
 * The parameters used to fetch a remote manifest.
 */

data class PlayerParameters(
  val credentials: PlayerCredentials?,
  val fetchURI: String) : Serializable

data class PlayerCredentials(
  val user: String,
  val password: String) : Serializable
