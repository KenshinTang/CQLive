package com.kapplication.cqlivesdk

interface LiveCallback {
    fun onSuccess(result: ArrayList<*>)
    fun onFailed(code: Int, reason: String)
}
