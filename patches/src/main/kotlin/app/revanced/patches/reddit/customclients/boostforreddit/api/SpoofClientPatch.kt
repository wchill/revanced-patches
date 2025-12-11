package app.revanced.patches.reddit.customclients.boostforreddit.api

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patches.reddit.customclients.spoofClientPatch
import app.revanced.patches.reddit.customclients.spoofUserAgentPatch
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.returnEarly
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

const val JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/boostforreddit/http/HttpUtils;"

val spoofClientPatch = spoofClientPatch { clientIdOption, redirectUriOption ->
    compatibleWith("com.rubenmayayo.reddit")

    val clientId by clientIdOption

    execute {
        // region Patch client id and redirect URI.

        getClientIdFingerprint.method.returnEarly(clientId!!)

        listOf(loginActivityOnCreateFingerprint, loginActivityAShouldOverrideUrlLoadingFingerprint).forEach { fingerprint ->
            fingerprint.method.let {
                fingerprint.stringMatches!!.forEach { match ->
                    val register = it.getInstruction<OneRegisterInstruction>(match.index).registerA
                    it.replaceInstruction(match.index, "const-string v$register, \"$redirectUriOption\"")
                }
            }
        }

        loginActivityAShouldOverrideUrlLoadingFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.IF_EQZ
            }
            addInstructions(
                index + 1,
                """
                const-string v1, "$redirectUriOption"
                const-string v2, "http://localhost"
                invoke-virtual {v6, v1, v2}, Ljava/lang/String;->replace(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Ljava/lang/String;
                move-result-object v6
                """
            )
        }

        jrawNewUrlFingerprint.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.NEW_INSTANCE && getReference<TypeReference>()?.type == "Ljava/net/URL;"
            }
            addInstructions(
                index,
                """
                invoke-static       { p0 }, $JRAW_NEW_URL_EXTENSION_CLASS_DESCRIPTOR->createUrl(Ljava/lang/String;)Ljava/net/URL;
                move-result-object  v0
                return-object v0
                """
            )
        }

        // endregion
    }
}

val userAgentPatch = spoofUserAgentPatch { userAgentOption ->
    compatibleWith("com.rubenmayayo.reddit")
    execute {
        // region Patch user agent.
        buildUserAgentFingerprint.method.let {
            buildUserAgentFingerprint.stringMatches!!.forEach { match ->
                val register = it.getInstruction<OneRegisterInstruction>(match.index).registerA
                it.replaceInstruction(match.index, "const-string v$register, \"$userAgentOption\"")
            }
        }
        // endregion
    }
}
