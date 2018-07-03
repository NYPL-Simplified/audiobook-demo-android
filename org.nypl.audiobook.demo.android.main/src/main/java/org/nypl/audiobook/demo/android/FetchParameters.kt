package org.nypl.audiobook.demo.android

import java.io.Serializable

data class FetchParameters(
  val user : String,
  val password : String,
  val fetchURI : String) : Serializable
