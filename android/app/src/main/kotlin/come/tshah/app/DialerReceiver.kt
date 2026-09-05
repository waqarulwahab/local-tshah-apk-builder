package come.tshah.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DialerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(launch)
    }
}
