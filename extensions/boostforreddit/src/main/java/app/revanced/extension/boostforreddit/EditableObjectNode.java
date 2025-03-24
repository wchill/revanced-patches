package app.revanced.extension.boostforreddit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.dean.jraw.util.JrawUtils;

import java.net.URI;
import java.net.URL;
import java.util.Date;
import java.util.Map;

// Required because the copy of Jackson databind has had unused code removed.
public class EditableObjectNode extends ObjectNode {
    public EditableObjectNode(JsonNodeFactory nc, Map<String, JsonNode> entries) {
        super(nc);
        _children.putAll(entries);
    }

    public JsonNode set(String fieldName, JsonNode value)
    {
        if (value == null) {
            value = nullNode();
        }
        _children.put(fieldName, value);
        return this;
    }

    public String data(String key) {
        JsonNode node = _children.get(key);
        if (node == null) {
            return null;
        } else {
            return node.asText();
        }
    }
}
