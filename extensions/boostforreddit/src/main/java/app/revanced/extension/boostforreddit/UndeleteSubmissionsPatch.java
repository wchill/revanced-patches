package app.revanced.extension.boostforreddit;

import android.util.LruCache;

import app.revanced.extension.shared.Logger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;

import net.dean.jraw.http.NetworkException;
import net.dean.jraw.models.Comment;
import net.dean.jraw.models.CommentNode;
import net.dean.jraw.models.Contribution;
import net.dean.jraw.models.JsonModel;
import net.dean.jraw.models.Listing;
import net.dean.jraw.models.PublicContribution;
import net.dean.jraw.models.Submission;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @noinspection unused
 */
public class UndeleteSubmissionsPatch {
    enum SubmissionType {
        POSTS,
        COMMENTS
    }

    private static final String EXTRA_EMOJI_CONTEXT_KEY = "extraEmoji";
    private static final String TRASH_CAN_EMOJI = "\uD83D\uDDD1";
    private static final String BROOM_EMOJI = "\uD83E\uDDF9";
    private static final String SIREN_EMOJI = "\uD83E\uDEA8";

    private static final String[] POSTS_FIELDS = {
            "id", "author", "author_fullname", "author_flair_text", "crosspost_parent", "link_flair_text", "selftext", "url"
    };

    private static final String[] COMMENTS_FIELDS = {
            "id", "author", "author_fullname", "author_flair_text", "body"
    };

    private static final String ARCTIC_SHIFT_BASE_URL = "https://arctic-shift.photon-reddit.com/api/";
    private static final UndeleteSubmissionsPatch INSTANCE = new UndeleteSubmissionsPatch();
    private static final Field jsonModelDataField;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final LruCache<String, JsonNode> cache;

    static {
        try {
            jsonModelDataField = JsonModel.class.getDeclaredField("data");
            jsonModelDataField.setAccessible(true);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to get JsonModel data field", e);
            throw new RuntimeException(e);
        }
    }

    public UndeleteSubmissionsPatch getInstance() {
        return INSTANCE;
    }

    private UndeleteSubmissionsPatch() {
        httpClient = new OkHttpClient();
        objectMapper = new ObjectMapper();
        cache = new LruCache<>(1000);
    }

    private void acquire() {
        // TODO
    }

    private void overwriteCommentJson(Comment comment, JsonNode newNode) {
        try {
            jsonModelDataField.set(comment, newNode);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to set JsonNode in Comment", e);
        }
    }

    private void mergeCommentJson(Comment comment, JsonNode dataNode) {
        EditableObjectNode editableNode = mergeJsonNodes(comment.getDataNode(), dataNode, COMMENTS_FIELDS, "body");

        if ("DELETED".equals(editableNode.data("collapsed_reason_code"))) {
            editableNode.set("collapsed", BooleanNode.FALSE);
        }

        if (isRemovedByAdmins(comment)) {
            editableNode.set(EXTRA_EMOJI_CONTEXT_KEY, new TextNode(SIREN_EMOJI));
        } else if (isRemovedByMod(comment)) {
            editableNode.set(EXTRA_EMOJI_CONTEXT_KEY, new TextNode(BROOM_EMOJI));
        } else if (isDeletedByUser(comment)) {
            editableNode.set(EXTRA_EMOJI_CONTEXT_KEY, new TextNode(TRASH_CAN_EMOJI));
        }

        cache.put(comment.getId(), editableNode);
        overwriteCommentJson(comment, editableNode);
    }

    private EditableObjectNode mergeJsonNodes(JsonNode oldNode, JsonNode newNode, String[] fields, String markdownField) {
        Map<String, JsonNode> map = new HashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = oldNode.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            map.put(entry.getKey(), entry.getValue());
        }
        for (String fieldName : fields) {
            JsonNode node = newNode.get(fieldName);
            if (node != null) {
                map.put(fieldName, node);
                if (fieldName.equals(markdownField)) {
                    String renderedHtml = MarkdownRenderer.render(node.asText());
                    map.put(markdownField + "_html", new TextNode(renderedHtml));
                }
            }
        }

        return new EditableObjectNode(JsonNodeFactory.instance, map);
    }

    private CommentNode fetchDeletedCommentSection(CommentNode rootNode) {
        Map<String, Comment> removedComments = new HashMap<>();

        for (CommentNode commentNode : new CommentNodeIterable(rootNode)) {
            Comment comment = commentNode.getComment();
            if (isContentRemoved(comment)) {
                String id = comment.getId();
                JsonNode cached = cache.get(id);
                if (cached == null) {
                    removedComments.put(id, comment);
                } else {
                    overwriteCommentJson(comment, cached);
                }
            }
        }

        if (!removedComments.isEmpty()) {
            JsonNode jsonNode = queryArcticShift(SubmissionType.COMMENTS, removedComments.keySet(), COMMENTS_FIELDS);
            for (Iterator<JsonNode> it = jsonNode.get("data").elements(); it.hasNext(); ) {
                JsonNode childNode = it.next();
                Comment comment = removedComments.get(childNode.get("id").asText());
                mergeCommentJson(comment, childNode);
            }
        }

        return rootNode;
    }

    private JsonNode fetchDeletedSubmission(Submission submission) {
        String id = submission.getId();
        JsonNode newNode = cache.get(id);
        if (newNode != null) {
            return newNode;
        }

        JsonNode undeletedData = queryArcticShift(SubmissionType.POSTS, List.of(id), POSTS_FIELDS);
        EditableObjectNode editableNode = mergeJsonNodes(submission.getDataNode(), undeletedData.get("data").get(0), POSTS_FIELDS, "selftext");

        if (isRemovedByAdmins(submission)) {
            editableNode.set(EXTRA_EMOJI_CONTEXT_KEY, new TextNode(SIREN_EMOJI));
        } else if (isRemovedByMod(submission)) {
            editableNode.set(EXTRA_EMOJI_CONTEXT_KEY, new TextNode(BROOM_EMOJI));
        } else if (isDeletedByUser(submission)) {
            editableNode.set(EXTRA_EMOJI_CONTEXT_KEY, new TextNode(TRASH_CAN_EMOJI));
        }

        cache.put(id, editableNode);
        return editableNode;
    }

    private JsonNode queryArcticShift(SubmissionType submissionType, Iterable<String> ids, String[] fields) {
        String url = ARCTIC_SHIFT_BASE_URL + submissionType.toString().toLowerCase() + "/ids?ids=" + String.join(",", ids);
        if (fields != null && fields.length > 0) {
            url += "&fields=" + String.join(",", fields);
        }
        try {
            Request request = new Request.Builder()
                    .get()
                    .url(url)
                    .build();
            acquire();
            try (Response response = httpClient.newCall(request).execute()) {
                String json = response.body().string();
                return objectMapper.readTree(json);
            }
        } catch (Exception e) {
            String error = String.format("Failed to fetch %s from Arctic Shift", url);
            Logger.printException(() -> error, e);
            return null;
        }
    }

    private boolean isContentRemoved(Comment content) {
        return isRemovedByAdmins(content) || isRemovedByMod(content) || isDeletedByUser(content);
    }

    private boolean isContentRemoved(Submission content) {
        return isRemovedByAdmins(content) || isRemovedByMod(content) || isDeletedByUser(content);
    }

    private boolean isRemovedByMod(Comment content) {
        return "[removed]".equals(content.getBody());
    }

    private boolean isDeletedByUser(Comment content) {
        return "[deleted]".equals(content.getBody());
    }

    private boolean isRemovedByMod(Submission content) {
        return "[removed]".equals(content.getSelftext()) || "moderator".equals(content.data("removed_by_category"));
    }

    private boolean isDeletedByUser(Submission content) {
        return "[deleted]".equals(content.getSelftext());
    }

    private boolean isRemovedByAdmins(PublicContribution content) {
        return "legal".equals(content.getRemovalReason()) || "content_takedown".equals(content.data("removed_by_category"));
    }

    public static Submission getSubmission(Submission submission) throws NetworkException {
        if (INSTANCE.isContentRemoved(submission)) {
            JsonNode submissionNode = INSTANCE.fetchDeletedSubmission(submission);
            CommentNode commentNode = INSTANCE.fetchDeletedCommentSection(submission.getComments());

            return new Submission(submissionNode, commentNode);
        }

        return submission;
    }
}
