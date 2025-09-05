package app.revanced.extension.boostforreddit.http.reddit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.revanced.extension.boostforreddit.utils.EditableObjectNode;
import app.revanced.extension.boostforreddit.utils.MarkdownRenderer;
import app.revanced.extension.boostforreddit.http.arcticshift.ArcticShift;
import app.revanced.extension.boostforreddit.http.AutoSavingCache;
import app.revanced.extension.boostforreddit.http.HttpUtils;
import app.revanced.extension.boostforreddit.http.wayback.WaybackMachine;
import app.revanced.extension.boostforreddit.http.wayback.WaybackResponse;
import app.revanced.extension.boostforreddit.utils.Emojis;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class RedditSubmissionUndeleteInterceptor implements Interceptor {
    private static final Pattern SUBMISSION_API_REGEX = Pattern.compile("^https?://\\w+\\.reddit\\.com/comments/");
    private static final Pattern GALLERY_REGEX = Pattern.compile("window\\.___r\\s*=\\s*(\\{.+\\})\\s*</script>", Pattern.DOTALL | Pattern.MULTILINE);
    private final AutoSavingCache submissionCache = new AutoSavingCache("RedditSubmissions", 1000);
    private final AutoSavingCache commentsCache = new AutoSavingCache("RedditComments", 100000);

    @NotNull
    @Override
    public Response intercept(@NotNull Chain chain) throws IOException {
        Request request = chain.request();
        String url = request.url().toString();

        if (!SUBMISSION_API_REGEX.matcher(url).find()) {
            return chain.proceed(request);
        }
        String[] pathParts = request.url().encodedPath().split("/");
        String submissionId = pathParts[pathParts.length - 1];

        Optional<String> cachedResponse = submissionCache.get(submissionId);
        JsonNode data;
        if (cachedResponse.isPresent()) {
            data = handle4xx(request, submissionId);
        } else {
            Response response = chain.proceed(request);

            if (response.isSuccessful()) {
                data = handle200(response, submissionId);
            } else {
                response.close();
                data = handle4xx(request, submissionId);
            }
        }
        // CacheUtils.writeStringToFile("/data/data/com.rubenmayayo.reddit/test.json", HttpUtils.getStringFromJson(data));
        return HttpUtils.makeJsonResponse(request, data);
    }

    private JsonNode handle200(Response redditResponse, String submissionId) throws IOException {
        String jsonStr = redditResponse.body().string();
        JsonNode json = HttpUtils.getJsonFromString(jsonStr);

        JsonNode submissionListing = json.get(0);
        JsonNode submission = submissionListing.get("data").get("children").get(0).get("data");
        if (RedditApiUtils.isContentRemoved(submission)) {
            submission = new EditableObjectNode(Map.of(
                    "kind", new TextNode("t3"),
                    "data", fetchDeletedSubmission(redditResponse.request(), submissionId, submission)
            ));
            submissionListing = RedditApiUtils.createListing(submissionListing, List.of(submission));
        }

        JsonNode commentsListing = json.get(1);
        ArrayNode comments = (ArrayNode) commentsListing.get("data").get("children");
        {
            Map<String, EditableObjectNode> removedComments = new HashMap<>();
            List<EditableObjectNode> topLevelReplies = new ArrayList<>();
            for (JsonNode comment : comments) {
                topLevelReplies.add(new EditableObjectNode(Map.of(
                        "kind", new TextNode("t1"),
                        "data", findDeletedComments(comment, removedComments))
                ));
            }
            if (!removedComments.isEmpty()) {
                JsonNode jsonNode = ArcticShift.getIds(ArcticShift.SubmissionType.COMMENTS, removedComments.keySet());
                for (JsonNode childNode : jsonNode.get("data")) {
                    String id = childNode.get("id").asText();
                    EditableObjectNode comment = removedComments.get(id);
                    updateCommentJson(comment, childNode);
                    removedComments.remove(id);
                }

                for (EditableObjectNode childNode : removedComments.values()) {
                    updateCommentJson(childNode, null);
                }

                commentsListing = RedditApiUtils.createListing(commentsListing, topLevelReplies);
            }
        }

        ArrayNode root = new ArrayNode(JsonNodeFactory.instance);
        root.add(submissionListing);
        root.add(commentsListing);
        return root;
    }

    private JsonNode handle4xx(Request request, String submissionId) throws IOException {
        EditableObjectNode submissionNode = new EditableObjectNode(Map.of(
                "kind", new TextNode("t3"),
                "data", fetchDeletedSubmission(request, submissionId, null)
        ));
        ArrayNode comments = ArcticShift.getCommentTree(submissionId);
        List<JsonNode> topLevelReplies = new ArrayList<>();
        for (JsonNode node : comments) {
            EditableObjectNode reply = EditableObjectNode.wrap(node.get("data"));
            checkReply(reply);
            topLevelReplies.add(new EditableObjectNode(Map.of(
                    "kind", new TextNode("t1"),
                    "data", reply
            )));
        }
        ArrayNode root = new ArrayNode(JsonNodeFactory.instance);
        EditableObjectNode submissionListing = RedditApiUtils.createListing(List.of(submissionNode));
        EditableObjectNode commentsListing = RedditApiUtils.createListing(topLevelReplies);
        root.add(submissionListing);
        root.add(commentsListing);
        return root;
    }

    private JsonNode fetchDeletedSubmission(Request request, String id, JsonNode dataNode) throws IOException {
        Optional<String> cachedJson = submissionCache.get(id);
        if (cachedJson.isPresent()) {
            return HttpUtils.getJsonFromString(cachedJson.get());
        }

        JsonNode undeletedData = ArcticShift.getIds(ArcticShift.SubmissionType.POSTS, List.of(id));

        EditableObjectNode editableNode;
        if (dataNode == null) {
            editableNode = new EditableObjectNode();
            editableNode.set(Emojis.EXTRA_EMOJI_CONTEXT_KEY, new TextNode(Emojis.PROHIBITED_EMOJI));
        } else {
            editableNode = EditableObjectNode.wrap(dataNode);
            RedditApiUtils.setRemovalEmoji(editableNode);
        }
        ArcticShift.updateSubmissionNode(editableNode, undeletedData.get("data").get(0));
        editableNode.set("archived", BooleanNode.TRUE);
        editableNode.set("stickied", BooleanNode.FALSE);
        editableNode.set("locked", BooleanNode.TRUE);
        editableNode.set("saved", BooleanNode.FALSE);
        editableNode.set("clicked", BooleanNode.FALSE);
        editableNode.set("likes", NullNode.instance);
        editableNode.setIfUnset("over_18", BooleanNode.FALSE);
        editableNode.setIfUnset("is_self", BooleanNode.FALSE);
        editableNode.setIfUnset("hidden", BooleanNode.FALSE);
        editableNode.setIfUnset("name", new TextNode("t3_" + id));
        editableNode.setIfUnset("suggested_sort", new TextNode("random"));
        editableNode.setIfUnset("author_flair_css_class", NullNode.instance);
        editableNode.setIfUnset("author_flair_text", NullNode.instance);
        editableNode.setIfUnset("link_flair_css_class", NullNode.instance);
        editableNode.setIfUnset("link_flair_text", NullNode.instance);

        /*
        JsonNode galleryNode = editableNode.get("gallery_data");
        if (galleryNode == null || galleryNode.isNull() || galleryNode.asText().isBlank()) {
            if (isRedditGallery(editableNode.get("url"))) {
                JsonNode data = getPostDataFromWayback(request, editableNode.get("url").asText());
                if (data != null) {
                    JsonNode media = data.get("posts").get("models").get("t3_" + id).get("media");
                    editableNode.set("gallery_data", media.get("gallery"));
                    editableNode.set("media_metadata", media.get("mediaMetadata"));
                    editableNode.set("is_gallery", BooleanNode.TRUE);
                }
            }
        }
        */

        submissionCache.put(id, HttpUtils.getStringFromJson(editableNode));
        return editableNode;
    }

    private EditableObjectNode findDeletedComments(JsonNode comment, Map<String, EditableObjectNode> deletedCommentIds) {
        EditableObjectNode wrappedComment = EditableObjectNode.wrap(comment);
        if (RedditApiUtils.isContentRemoved(wrappedComment)) {
            deletedCommentIds.put(wrappedComment.get("id").asText(), wrappedComment);
        }
        JsonNode replies = wrappedComment.get("replies");
        if (replies != null && !replies.isNull() && !replies.asText().isEmpty()) {
            ArrayNode newReplyArray = new ArrayNode(JsonNodeFactory.instance);
            for (JsonNode reply : replies) {
                EditableObjectNode wrappedReply = findDeletedComments(reply, deletedCommentIds);
                newReplyArray.add(wrappedReply);
            }
            wrappedComment.set("replies", newReplyArray);
        }
        return wrappedComment;
    }

    private void updateCommentJson(EditableObjectNode comment, JsonNode newComment) {
        RedditApiUtils.setRemovalEmoji(comment);
        if ("DELETED".equals(comment.data("collapsed_reason_code"))) {
            comment.set("collapsed", BooleanNode.FALSE);
        }
        ArcticShift.updateCommentNode(comment, newComment);
        commentsCache.put(comment.get("id").asText(), HttpUtils.getStringFromJson(comment));
    }

    private void checkReply(EditableObjectNode node) {
        node.setIfUnset("saved", BooleanNode.FALSE);
        node.setIfUnset("controversiality", new IntNode(0));
        node.setIfUnset("score_hidden", BooleanNode.FALSE);
        node.set("locked", BooleanNode.TRUE);
        node.set("likes", NullNode.instance);

        if (node.get("body") != null) {
            String renderedHtml = MarkdownRenderer.render(node.get("body").asText());
            node.set("body_html", new TextNode(renderedHtml));
        }

        JsonNode replies = node.get("replies");
        if (replies == null) {
            node.set("replies", new TextNode(""));
        } else {
            EditableObjectNode newReplies = EditableObjectNode.wrap(replies);
            EditableObjectNode dataNode = EditableObjectNode.wrap(replies.get("data"));
            ArrayNode newReplyArray = new ArrayNode(JsonNodeFactory.instance);
            for (JsonNode child : dataNode.get("children")) {
                EditableObjectNode newChild = EditableObjectNode.wrap(child);
                EditableObjectNode newChildData = EditableObjectNode.wrap(newChild.get("data"));
                checkReply(newChildData);
                newChild.set("data", newChildData);
                newReplyArray.add(newChild);
            }
            dataNode.set("children", newReplyArray);
            newReplies.set("data", dataNode);
            node.set("replies", newReplies);
        }
    }

    private JsonNode getPostDataFromWayback(Request request, String galleryUrl) throws IOException {
        WaybackResponse response = WaybackMachine.getFromWayback(request, galleryUrl);
        String html = response.getResponse().body().string();
        Matcher matcher = GALLERY_REGEX.matcher(html);
        if (!response.found()) {
            return null;
        }
        if (matcher.find()) {
            String json = matcher.group(1);
            json = json.replaceAll("(web\\.archive\\.org/web/\\d+)/", "$1if_/");
            return HttpUtils.getJsonFromString(json);
        }
        return null;
    }

    private boolean isRedditGallery(JsonNode urlNode) {
        if (urlNode == null || urlNode.isNull()) return false;
        String url = urlNode.asText();
        return url != null && url.contains("reddit.com/gallery/");
    }

    private boolean hasImage(String url) {
        if (url == null) return false;
        if (url.isBlank()) return false;
        return url.endsWith(".jpg") || url.endsWith(".png") || url.endsWith(".webp") || url.endsWith(".gif") || url.endsWith(".jpeg");
    }
}
