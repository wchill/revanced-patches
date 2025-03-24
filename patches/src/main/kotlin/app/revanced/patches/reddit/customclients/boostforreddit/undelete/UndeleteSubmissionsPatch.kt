package app.revanced.patches.reddit.customclients.boostforreddit.undelete

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.revanced.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patcher.util.smali.toInstructions
import app.revanced.patches.reddit.customclients.boostforreddit.misc.extension.sharedExtensionPatch
import app.revanced.util.addInstructionsAtControlFlowLabel
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstruction
import app.revanced.util.indexOfFirstInstructionOrThrow
import app.revanced.util.indexOfFirstInstructionReversed
import app.revanced.util.indexOfFirstLiteralInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/boostforreddit/UndeleteSubmissionsPatch;"
const val EXTRA_EMOJI_CONTEXT_KEY = "extraEmoji"
const val EXTRA_EMOJI_GETTER = "getExtraEmoji"

@Suppress("unused")
val undeletePostsPatch = bytecodePatch(
    name="Automatically undelete removed posts"
) {
    dependsOn(sharedExtensionPatch)
    compatibleWith("com.rubenmayayo.reddit")

    execute {
        // region getSubmission handler.
        getSubmissionFingerprint.method.apply {
            val submissionRegister = "p1"

            val returnIndex = indexOfFirstInstructionReversed(Opcode.RETURN_OBJECT)
            addInstructions(
                returnIndex,
                """
                    invoke-static { $submissionRegister }, $EXTENSION_CLASS_DESCRIPTOR->getSubmission(Lnet/dean/jraw/models/Submission;)Lnet/dean/jraw/models/Submission;
                    move-result-object $submissionRegister
                """,
            )
        }
        // endregion

        // region Add additional field and marshaling code for ContributionModel.
        val fieldType = "Ljava/lang/String;"
        contributionModelConstructorFingerprint.classDef.apply {
            fields.add(ImmutableField(
                type,
                EXTRA_EMOJI_CONTEXT_KEY,
                fieldType,
                AccessFlags.PROTECTED.value,
                null,
                null,
                null,
            ).toMutable())
            methods.add(ImmutableMethod(
                type,
                EXTRA_EMOJI_GETTER,
                emptyList(),
                fieldType,
                AccessFlags.PUBLIC.value,
                null,
                null,
                MutableMethodImplementation(2),
            ).toMutable().apply {
                addInstructions(
                    """
                    iget-object v0, p0, $type->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                    return-object v0
                """,
                )
            },)
        }
        contributionModelConstructorFingerprint.method.apply {
            addInstructions(
                instructions.lastIndex,
                """
                invoke-virtual      {p1}, Landroid/os/Parcel;->readString()$fieldType
                move-result-object  v0
                iput-object         v0, p0, ${this.definingClass}->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                """
            )
        }
        contributionModelWriteToParcelFingerprint.method.apply {
            addInstructions(
                instructions.lastIndex,
                """
                iget-object         v0, p0, ${this.definingClass}->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                invoke-virtual      {p1, v0}, Landroid/os/Parcel;->writeString($fieldType)V
                """
            )
        }
        // endregion

        // region Modify SubmissionModel/CommentModel deserialization.
        arrayOf(submissionModelDeserializeFingerprint, commentModelDeserializeFingerprint).forEach {
            it.method.apply {
                val index = indexOfFirstInstruction(Opcode.INVOKE_VIRTUAL)
                addInstructions(
                    index,
                    """
                    const-string        v1, "$EXTRA_EMOJI_CONTEXT_KEY"
                    invoke-virtual      {p0, v1}, Lnet/dean/jraw/models/JsonModel;->data(Ljava/lang/String;)Ljava/lang/String;
                    move-result-object  v1
                    iput-object         v1, v0, Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;->$EXTRA_EMOJI_CONTEXT_KEY:$fieldType
                    """
                )
            }
        }
        // endregion

        // region Extend emojis used for BabushkaText.
        val babushkaText = "\$a"
        setCommentBabushkaTextFingerprint.method.apply {
            val babushkaTextCheckGotoIndex = indexOfFirstInstruction(Opcode.GOTO)
            val gotoTarget = getInstruction<BuilderInstruction10t>(babushkaTextCheckGotoIndex).target
            replaceInstruction(babushkaTextCheckGotoIndex - 1, BuilderInstruction10t(Opcode.GOTO, gotoTarget))
            val checkInstructions = """
                    invoke-virtual      {p1}, Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;->$EXTRA_EMOJI_GETTER()Ljava/lang/String;
                    move-result-object  v2
                    invoke-static       {v2}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                    move-result         v0
                    """.toInstructions(this).toMutableList()
            checkInstructions.add(BuilderInstruction21t(Opcode.IF_EQZ, 0, gotoTarget))
            addInstructions(
                babushkaTextCheckGotoIndex,
                checkInstructions,
            )

            val babushkaTextLoadIndex = indexOfFirstInstructionReversed(Opcode.GOTO_16) + 1
            addInstructionsAtControlFlowLabel(
                babushkaTextLoadIndex,
                """
                    invoke-virtual      {p1}, Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;->$EXTRA_EMOJI_GETTER()Ljava/lang/String;
                    move-result-object  v2
                    invoke-static       {v2}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                    move-result         v0
                    if-nez              v0, :continue
                    iget-object         v0, p0, Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;->distinguishedTv:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;
                    new-instance        v1, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;
                    invoke-direct       {v1, v2}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;-><init>(Ljava/lang/String;)V
                    invoke-virtual      {v1}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;->r()Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;
                    move-result-object  v1
                    invoke-virtual      {v0, v1}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;->m(Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;)V
                    :continue
                    nop
                    """
            )
        }
        setSubmissionBabushkaTextFingerprint.method.apply {
            // iget-object         p1, p0, Lcom/rubenmayayo/reddit/ui/adapters/SubmissionViewHolder;->infoTop:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;
            // invoke-virtual      {p1}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;->o()V

            var babushkaTextLoadIndex = indexOfFirstInstruction {
                val methodReference = getReference<MethodReference>()
                opcode == Opcode.INVOKE_VIRTUAL && methodReference?.definingClass == "Lcom/rubenmayayo/reddit/models/reddit/PublicContributionModel;" && methodReference.name == "x"
            }
            /*
            print(babushkaTextLoadIndex)
            babushkaTextLoadIndex = indexOfFirstInstructionOrThrow {
                getReference<StringReference>()?.contains("\uD83D\uDD12") == true
            } - 8
            print(babushkaTextLoadIndex)

             */
            addInstructionsAtControlFlowLabel(
                babushkaTextLoadIndex,
                """
                    invoke-virtual      {p1}, Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;->$EXTRA_EMOJI_GETTER()Ljava/lang/String;
                    move-result-object  v0
                    invoke-static       {v0}, Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z
                    move-result         v4
                    if-nez              v4, :continue
                    iget-object         v4, p0, $definingClass->infoTop:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;
                    iget-object         v5, p0, $definingClass->l:Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;
                    invoke-virtual      {v4, v5}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;->m(Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;)V
                    new-instance        v5, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;
                    invoke-direct       {v5, v0}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;-><init>(Ljava/lang/String;)V
                    invoke-virtual      {v5}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText$babushkaText;->r()Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;
                    move-result-object  v5
                    invoke-virtual      {v4, v5}, Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText;->m(Lcom/rubenmayayo/reddit/ui/customviews/BabushkaText$babushkaText;)V
                    :continue
                    nop
                    """
            )
        }

        // endregion
    }
}
