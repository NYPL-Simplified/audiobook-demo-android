package org.librarysimplified.audiobook.demo.main_ui

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import org.slf4j.Logger

/**
 * Some utilities to show error dialog boxes.
 */

class ExampleErrorDialogUtilities {

  companion object {

    /**
     * Show an error dialog, running the given runnable when the user dismisses
     * the message.
     *
     * @param ctx     The activity
     * @param log     The log handle
     * @param message The error message
     * @param x       The optional exception
     * @param r       The runnable to execute on dismissal
     */

    fun showErrorWithRunnable(
      ctx: Activity,
      log: Logger,
      message: String,
      x: Throwable?,
      r: Runnable) {
      log.error("{}: ", message, x)

      ExampleUIThread.runOnUIThread(
        Runnable {
          val sb = StringBuilder()
          sb.append(message)

          if (x != null) {
            sb.append("\n\n")
            sb.append(x)
          }

          val b = AlertDialog.Builder(ctx)
          b.setNeutralButton("OK", null)
          b.setMessage(sb.toString())
          b.setTitle("Error")
          b.setCancelable(true)
          b.setOnDismissListener(
            object : DialogInterface.OnDismissListener {
              override fun onDismiss(a: DialogInterface) {
                r.run()
              }
            })

          val a = b.create()
          a.show()
        })
    }
  }

}