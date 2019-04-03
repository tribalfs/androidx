package com.google.r4a.examples.explorerapp

import com.google.r4a.*
import com.google.r4a.adapters.*
import com.google.r4a.examples.explorerapp.common.adapters.*

class SignupFragment: ComposeFragment() {
    override fun compose() {
        with(composer) {
            call(
                    0,
                    { true },
                    { SignupScreen() }
            )
        }
    }
}