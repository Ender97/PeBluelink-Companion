package com.accord.pebluelinkcompanion

import android.content.Context
import android.util.Log
import com.getpebble.android.kit.PebbleKit
import com.getpebble.android.kit.PebbleKit.PebbleDataReceiver
import com.getpebble.android.kit.util.PebbleDictionary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "PebbleReceiver"

private const val KEY_CMD          = 0
private const val KEY_RESULT       = 1
private const val KEY_MSG          = 2
private const val KEY_LOCKED       = 3
private const val KEY_TEMP         = 4
private const val KEY_RANGE        = 5
private const val KEY_CHARGING     = 6
private const val KEY_CHARGE_TIME  = 7
private const val KEY_TWELVE_SOC   = 8
private const val KEY_CLIMATE_TEMP = 9

private const val KEY_EV_SOC       = 10

val WATCH_APP_UUID: UUID = UUID.fromString("765d084f-3d8c-4da8-a278-6b45ec18744c")

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

fun buildDataReceiver(context: Context): PebbleDataReceiver {
    return object : PebbleDataReceiver(WATCH_APP_UUID) {

        override fun receiveData(ctx: Context, transactionId: Int, dict: PebbleDictionary) {
            PebbleKit.sendAckToPebble(ctx, transactionId)

            val cmd = dict.getString(KEY_CMD)
            Log.d(TAG, "Received command: $cmd")

            val repo = BluelinkRepository(ctx)
            if (!repo.isConfigured) {
                sendError(ctx, "Not configured")
                return
            }

            scope.launch {
                when (cmd) {
                    "status" -> pushStatus(ctx, repo)
                    "lock"   -> handleCommand(ctx) { repo.lock()   }
                    "unlock" -> handleCommand(ctx) { repo.unlock() }
                    "stop"   -> handleCommand(ctx) { repo.stop()   }
                    "start"  -> {
                        val tempF = dict.getInteger(KEY_CLIMATE_TEMP)?.toInt() ?: 70
                        handleCommand(ctx) { repo.start(tempF) }
                    }
                    "chargestart" -> handleCommand(ctx) { repo.chargeStart() }
                    "chargestop"  -> handleCommand(ctx) { repo.chargeStop()  }
                    else -> {
                        Log.w(TAG, "Unknown command: $cmd")
                        sendError(ctx, "Unknown cmd")
                    }
                }
            }
        }
    }
}

private suspend fun pushStatus(context: Context, repo: BluelinkRepository) {
    repo.getStatus()
        .onSuccess { status ->
            val dict = PebbleDictionary()
            dict.addInt32(KEY_LOCKED,      if (status.locked)   1 else 0)
            dict.addInt32(KEY_RANGE,       status.range)
            dict.addInt32(KEY_CHARGING,    if (status.charging) 1 else 0)
            dict.addInt32(KEY_CHARGE_TIME, status.chargeTimeMins)
            dict.addInt32(KEY_TWELVE_SOC,  status.twelveSoc)
            dict.addInt32(KEY_EV_SOC,      status.soc)
            PebbleKit.sendDataToPebble(context, WATCH_APP_UUID, dict)
            Log.d(TAG, "Status pushed: $status")
        }
        .onFailure { e ->
            Log.e(TAG, "Status error: ${e.message}")
            sendError(context, "Status failed")
        }
}

private suspend fun handleCommand(
    context: Context,
    block: suspend () -> BluelinkResult
) {
    val result = block()
    val dict = PebbleDictionary()
    when (result) {
        is BluelinkResult.Success -> {
            dict.addString(KEY_RESULT, "ok")
            dict.addString(KEY_MSG,    result.message)
        }
        is BluelinkResult.Error -> {
            dict.addString(KEY_RESULT, "error")
            dict.addString(KEY_MSG,    result.message)
        }
    }
    PebbleKit.sendDataToPebble(context, WATCH_APP_UUID, dict)
}

private fun sendError(context: Context, msg: String) {
    val dict = PebbleDictionary()
    dict.addString(KEY_RESULT, "error")
    dict.addString(KEY_MSG, msg)
    PebbleKit.sendDataToPebble(context, WATCH_APP_UUID, dict)
}
