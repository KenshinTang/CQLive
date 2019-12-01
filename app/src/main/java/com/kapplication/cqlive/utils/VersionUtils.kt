package com.kapplication.cqlive.utils

import android.content.Context

class VersionUtils{
    companion object {
        fun isTCL(context: Context) : Boolean {
            return Utils.getVersionName(context).contains("TCL", false)
        }

        fun isPENGUIN1SA4062(context: Context): Boolean {
            return Utils.getVersionName(context).contains("PENGUIN1S", false)
        }
    }
}