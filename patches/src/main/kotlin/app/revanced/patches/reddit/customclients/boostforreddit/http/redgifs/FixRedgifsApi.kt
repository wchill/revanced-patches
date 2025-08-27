package app.revanced.patches.reddit.customclients.boostforreddit.http.redgifs

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.boostforreddit.http.OKHTTP_EXTENSION_CLASS_DESCRIPTOR
import app.revanced.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val fixRedgifsApi = bytecodePatch(
    name="Fix Redgifs API"
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.rubenmayayo.reddit")

    execute {
        installRedgifsInterceptorFingerprint.method.apply {
            val index = instructions.indexOfFirst {
                if (it.opcode != Opcode.INVOKE_VIRTUAL) return@indexOfFirst false
                val reference = (it as ReferenceInstruction).reference as MethodReference
                reference.name == "build" && reference.definingClass == "Lokhttp3/OkHttpClient${'$'}Builder;"
            }
            addInstructions(
                index,
                """
                invoke-static       { }, $OKHTTP_EXTENSION_CLASS_DESCRIPTOR->installRedgifsInterceptor()Lokhttp3/OkHttpClient${'$'}Builder;
                move-result-object  v0
                """
            )
        }
    }
}