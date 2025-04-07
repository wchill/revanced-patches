package app.revanced.extension.boostforreddit.http.arcticshift;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import app.revanced.extension.boostforreddit.http.HttpUtils;
import app.revanced.extension.boostforreddit.utils.EditableObjectNode;
import app.revanced.extension.boostforreddit.utils.MarkdownRenderer;

public class ArcticShift {
    public enum SubmissionType {
        POSTS,
        COMMENTS
    }

    private static final String ARCTIC_SHIFT_BASE_URL = "https://arctic-shift.photon-reddit.com/api/";

    private static final String[] POSTS_FIELDS = {
            "id", "title", "author", "author_fullname", "author_flair_text", "crosspost_parent",
            "link_flair_text", "selftext", "url", "preview", "gallery_data", "post_hint", "domain",
            "is_reddit_media_domain", "is_self", "is_video", "is_gallery", "media", "media_embed",
            "thumbnail"
    };

    private static final String[] COMMENTS_FIELDS = {
            "id", "author", "author_fullname", "author_flair_text", "body"
    };

    public static JsonNode getIds(SubmissionType submissionType, Iterable<String> ids) throws IOException {
        String url = ARCTIC_SHIFT_BASE_URL + submissionType.toString().toLowerCase() + "/ids?ids=" + String.join(",", ids);
        return HttpUtils.getJson(url);
    }

    public static ArrayNode getCommentTree(String linkId) throws IOException {
        //return queryArcticShiftForCommentTree(id, null, null, null, null);
        String url = ARCTIC_SHIFT_BASE_URL + "comments/tree?limit=30000&link_id=" + linkId;
        return (ArrayNode) HttpUtils.getJson(url).get("data");
    }

    public static JsonNode getSubredditInfoById(String id) throws IOException {
        String url = ARCTIC_SHIFT_BASE_URL + "subreddits/ids?ids=" + id;
        JsonNode data = HttpUtils.getJson(url);
        return new EditableObjectNode(
                Map.of(
                        "kind", new TextNode("t5"),
                        "data", data.get("data").get(0)
                )
        );
    }

    public static ArrayNode getSubredditPosts(String subredditName, Integer limit) throws IOException {
        return getSubredditPosts(subredditName, limit, null, null);
    }

    public static ArrayNode getSubredditPosts(String subredditName, Integer limit, String before, String after) throws IOException {
        String url = String.format(ARCTIC_SHIFT_BASE_URL + "posts/search?sort=desc&subreddit=%s&limit=", subredditName);
        if (limit == null) {
            url += "auto";
        } else if (limit > 0 && limit <= 100) {
            url += limit;
        } else {
            throw new IllegalArgumentException("Limit must either be null or between 1 and 100 inclusive");
        }

        if (before != null) {
            url += "&after=" + before;
        }

        if (after != null) {
            url += "&before=" + after;
        }

        return (ArrayNode) HttpUtils.getJson(url).get("data");
    }

    public static void updateSubmissionNode(EditableObjectNode oldNode, JsonNode newNode) {
        mergeJsonNodes(oldNode, newNode, POSTS_FIELDS, "selftext");
    }

    public static void updateCommentNode(EditableObjectNode oldNode, JsonNode newNode) {
        mergeJsonNodes(oldNode, newNode, COMMENTS_FIELDS, "body");
    }

    private static void mergeJsonNodes(EditableObjectNode oldNode, JsonNode newNode, String[] fields, String markdownField) {
        if (oldNode == null) {
            throw new IllegalArgumentException("oldNode must not be null");
        }

        if (newNode != null) {
            for (Iterator<String> it = newNode.fieldNames(); it.hasNext(); ) {
                String fieldName = it.next();
                if (oldNode.get(fieldName) == null) {
                    oldNode.set(fieldName, newNode.get(fieldName));
                }
            }

            for (String fieldName : fields) {
                JsonNode node = newNode.get(fieldName);
                if (node != null) {
                    oldNode.set(fieldName, node);
                    if (fieldName.equals(markdownField)) {
                        String renderedHtml = MarkdownRenderer.render(node.asText());
                        oldNode.set(markdownField + "_html", new TextNode(renderedHtml));
                    }
                }
            }
        }
    }
}
