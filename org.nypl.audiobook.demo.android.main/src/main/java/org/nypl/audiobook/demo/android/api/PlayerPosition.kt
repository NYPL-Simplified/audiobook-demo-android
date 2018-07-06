package org.nypl.audiobook.demo.android.api

data class PlayerPosition(
  val title: String?,
  val part: Int,
  val chapter: Int,
  val offset: Int)
