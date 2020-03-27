package com.kapplication.cqlive.behavior

import com.starcor.xulapp.XulPresenter
import com.starcor.xulapp.behavior.XulBehaviorManager
import com.starcor.xulapp.behavior.XulUiBehavior

class MosaicBehavior(xulPresenter: XulPresenter) : BaseBehavior(xulPresenter) {

    companion object {
        const val NAME = "MosaicBehavior"

        fun register() {
            XulBehaviorManager.registerBehavior(NAME,
                    object : XulBehaviorManager.IBehaviorFactory {
                        override fun createBehavior(xulPresenter: XulPresenter): XulUiBehavior {
                            return MosaicBehavior(xulPresenter)
                        }

                        override fun getBehaviorClass(): Class<*> {
                            return MosaicBehavior::class.java
                        }
                    })
        }
    }
}