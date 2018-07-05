package org.nypl.audiobook.demo.android.api

interface PlayerDownloadTaskType {

  fun fetch()

  fun delete()

  val progress: Double

  val id: String

}