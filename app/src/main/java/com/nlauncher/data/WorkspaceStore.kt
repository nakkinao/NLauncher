package com.nlauncher.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.nlauncher.model.Workspace
import org.json.JSONObject
import java.io.File

class WorkspaceStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "workspace.json"))

    fun load(): Workspace? = try {
        val bytes = file.readFully()
        Workspace.fromJson(JSONObject(String(bytes, Charsets.UTF_8)))
    } catch (e: Exception) {
        if (e !is java.io.FileNotFoundException) Log.w(TAG, "load failed", e)
        null
    }

    fun save(ws: Workspace) {
        val data = ws.toJson().toString().toByteArray(Charsets.UTF_8)
        var out: java.io.FileOutputStream? = null
        try {
            out = file.startWrite()
            out.write(data)
            file.finishWrite(out)
        } catch (e: Exception) {
            Log.e(TAG, "save failed", e)
            if (out != null) file.failWrite(out)
        }
    }

    companion object {
        private const val TAG = "WorkspaceStore"
    }
}
