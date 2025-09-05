package app.revanced.patches.reddit.customclients.boostforreddit.undelete.imgur

import app.revanced.patcher.fingerprint


internal val installImgurPaidOkHttpInterceptorFingerprint = fingerprint {
    custom { method, _ -> method.name == "d" && method.definingClass == "Lbc/a;" }
}

internal val installImgurFreeOkHttpInterceptorFingerprint = fingerprint {
    custom { method, _ -> method.name == "e" && method.definingClass == "Lbc/a;" }
}