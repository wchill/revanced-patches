package app.revanced.patches.reddit.customclients.boostforreddit.debug

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/boostforreddit/ExceptionHook;"

@Suppress("unused")
val hookExceptionHandler = bytecodePatch(
    name="Hook exception handler",
    use=false
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.rubenmayayo.reddit")

    execute {
        exceptionHandlerFingerprint.method.apply {
            addInstructions(
                0,
                """
                    invoke-static { p0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->handleException(Ljava/lang/Throwable;Ljava/lang/String;)V
                """,
            )
        }
    }
}