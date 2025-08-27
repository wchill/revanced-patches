package app.revanced.patches.reddit.customclients.boostforreddit.http.redgifs

import app.revanced.patcher.fingerprint

internal val installRedgifsInterceptorFingerprint = fingerprint {
    custom { method, _ -> method.name == "j" && method.definingClass == "Lfc/a;" }
}