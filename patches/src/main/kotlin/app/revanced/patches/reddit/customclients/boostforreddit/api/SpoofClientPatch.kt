package app.revanced.patches.reddit.customclients.boostforreddit.api

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patches.reddit.customclients.spoofClientPatch
import app.revanced.patches.reddit.customclients.spoofUserAgentPatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

val spoofClientPatch = spoofClientPatch() { clientIdOption, redirectUriOption ->
    compatibleWith("com.rubenmayayo.reddit")
    execute {
        // region Patch client id and redirect URI.
        getClientIdFingerprint.method.addInstructions(
            0,
            """
                 const-string v0, "$clientIdOption"
                 return-object v0
            """,
        )

        listOf(loginActivityOnCreateFingerprint, loginActivityAShouldOverrideUrlLoadingFingerprint).forEach { fingerprint ->
            fingerprint.method.let {
                fingerprint.stringMatches!!.forEach { match ->
                    val register = it.getInstruction<OneRegisterInstruction>(match.index).registerA
                    it.replaceInstruction(match.index, "const-string v$register, \"$redirectUriOption\"")
                }
            }
        }

        // endregion
    }
}

val userAgentPatch = spoofUserAgentPatch() { userAgentOption ->
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