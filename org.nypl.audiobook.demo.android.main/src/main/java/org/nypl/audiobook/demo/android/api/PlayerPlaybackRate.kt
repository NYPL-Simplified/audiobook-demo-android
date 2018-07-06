package org.nypl.audiobook.demo.android.api

enum class PlayerPlaybackRate(val speed: Double) {
  THREE_QUARTERS_TIME(0.75),
  NORMAL_TIME(1.0),
  ONE_AND_A_QUARTER_TIME(1.25),
  ONE_AND_A_HALF_TIME(1.50),
  DOUBLE_TIME(2.0);
}