package app.revanced.patches.reddit.customclients.boostforreddit.debug

import app.revanced.patcher.fingerprint

internal val exceptionHandlerFingerprint = fingerprint {
    custom { method, classDef ->
        method.definingClass == "Lhe/h0;" && method.name == "j0"
    }
}