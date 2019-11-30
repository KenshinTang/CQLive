package com.kapplication.cqlive.utils

import android.content.Context

class VersionUtils{
    companion object {
        fun isTCL(context: Context) : Boolean {
            return Utils.getVersionName(context).contains("TCL", false)
        }
    }
}