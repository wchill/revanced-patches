package app.revanced.patches.reddit.customclients.boostforreddit.undelete

import app.revanced.patcher.fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal val getSubmissionFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("Lnet/dean/jraw/models/Submission")
    parameters("Lnet/dean/jraw/http/SubmissionRequest")
    custom { method, _ -> method.definingClass == "Lnet/dean/jraw/RedditClient;" }
}

internal val paginateUserFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("Lnet/dean/jraw/models/Listing")
    parameters("Z")
    custom { method, _ -> method.definingClass == "Lnet/dean/jraw/paginators/UserContributionPaginator;" }
}

// Lcom/rubenmayayo/reddit/ui/adapters/CommentViewHolder;->O(Lcom/rubenmayayo/reddit/models/reddit/CommentModel;
internal val setCommentBabushkaTextFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("V")
    parameters("Lcom/rubenmayayo/reddit/models/reddit/CommentModel")
    strings("\uD83D\uDD12")
}

// Lcom/rubenmayayo/reddit/ui/adapters/SubmissionViewHolder;->n0(Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel;)V
internal val setSubmissionBabushkaTextFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("V")
    parameters("Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel")
    strings("\uD83D\uDD12")
}

internal val contributionModelConstructorFingerprint = fingerprint {
    accessFlags(AccessFlags.PROTECTED, AccessFlags.CONSTRUCTOR)
    returns("V")
    custom { method, _ -> method.definingClass == "Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;" }
}

internal val contributionModelWriteToParcelFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC)
    returns("V")
    custom { method, _ -> method.name == "writeToParcel" && method.definingClass == "Lcom/rubenmayayo/reddit/models/reddit/ContributionModel;" }
}

internal val submissionModelDeserializeFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returns("Lcom/rubenmayayo/reddit/models/reddit/SubmissionModel")
    parameters("Lnet/dean/jraw/models/Submission")
}

internal val commentModelDeserializeFingerprint = fingerprint {
    accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)
    returns("Lcom/rubenmayayo/reddit/models/reddit/CommentModel")
    parameters("Lnet/dean/jraw/models/Comment")
}