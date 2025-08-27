package app.revanced.patches.reddit.customclients.boostforreddit.http

import app.revanced.patcher.fingerprint

internal val installOkHttpInterceptorFingerprint = fingerprint {
    custom { method, _ -> method.name == "c" && method.definingClass == "Ltb/a;" }
}