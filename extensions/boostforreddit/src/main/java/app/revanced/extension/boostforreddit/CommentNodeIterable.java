package app.revanced.extension.boostforreddit;

import net.dean.jraw.models.CommentNode;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;

// Needed because of ProGuard messing up class names.
public class CommentNodeIterable implements Iterable<CommentNode> {
    static class CommentNodeIterator implements Iterator<CommentNode> {
        private final Queue<CommentNode> queue;

        CommentNodeIterator(CommentNode commentNode) {
            queue = new LinkedList<>(commentNode.getChildren());
        }

        @Override
        public boolean hasNext() {
            return !queue.isEmpty();
        }

        @Override
        public CommentNode next() {
            if (queue.isEmpty()) {
                throw new NoSuchElementException();
            }
            CommentNode node = queue.remove();
            queue.addAll(node.getChildren());
            return node;
        }
    }

    private final CommentNode rootNode;

    public CommentNodeIterable(CommentNode commentNode) {
        rootNode = commentNode;
    }

    @Override
    public Iterator<CommentNode> iterator() {
        return new CommentNodeIterator(rootNode);
    }
}
