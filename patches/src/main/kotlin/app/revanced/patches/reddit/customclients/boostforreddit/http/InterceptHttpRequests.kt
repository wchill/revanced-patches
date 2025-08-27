package app.revanced.patches.reddit.customclients.boostforreddit.http

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.revanced.util.indexOfFirstInstructionReversed
import com.android.tools.smali.dexlib2.Opcode


internal const val OKHTTP_EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/boostforreddit/http/OkHttpRequestHook;"

@Suppress("unused")
val interceptHttpRequests = bytecodePatch(
    name="Intercept HTTP requests"
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.rubenmayayo.reddit")

    execute {
        installOkHttpInterceptorFingerprint.method.apply {
            val index = indexOfFirstInstructionReversed(Opcode.INVOKE_VIRTUAL)
            addInstructions(
                index,
                """
                invoke-static       { p0 }, $OKHTTP_EXTENSION_CLASS_DESCRIPTOR->installInterceptor(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
                move-result-object  p0
                """
            )
        }
    }
}