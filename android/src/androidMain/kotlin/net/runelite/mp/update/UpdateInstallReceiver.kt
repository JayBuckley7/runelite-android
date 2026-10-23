package net.runelite.mp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/** Result of [UpdateChecker.installSession]: confirm screen when the system wants one, else the outcome. */
class UpdateInstallReceiver : BroadcastReceiver()
{
    override fun onReceive(context: Context, intent: Intent)
    {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status)
        {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
            {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm == null)
                {
                    UpdateGate.onInstallFailed("installer gave no confirm screen")
                    return
                }
                // The splash is in the foreground, so starting an activity from here is allowed.
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            // Usually never seen: a self-update kills this process before the broadcast lands.
            PackageInstaller.STATUS_SUCCESS -> Log.i(TAG, "update installed")
            else ->
            {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "install failed ($status)"
                Log.w(TAG, "update install failed: $message")
                UpdateGate.onInstallFailed(message)
            }
        }
    }

    private companion object
    {
        const val TAG = "UpdateInstallReceiver"
    }
}
