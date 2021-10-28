package org.yaml.snakeyaml.nodes;

import java.util.Iterator;
import java.util.List;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.error.Mark;

public class SequenceNode extends CollectionNode {

    private final List value;

    public SequenceNode(Tag tag, boolean resolved, List value, Mark startMark, Mark endMark, DumperOptions.FlowStyle flowStyle) {
        super(tag, startMark, endMark, flowStyle);
        if (value == null) {
            throw new NullPointerException("value in a Node is required.");
        } else {
            this.value = value;
            this.resolved = resolved;
        }
    }

    public SequenceNode(Tag tag, List value, DumperOptions.FlowStyle flowStyle) {
        this(tag, true, value, (Mark) null, (Mark) null, flowStyle);
    }

    /** @deprecated */
    @Deprecated
    public SequenceNode(Tag tag, List value, Boolean style) {
        this(tag, value, DumperOptions.FlowStyle.fromBoolean(style));
    }

    /** @deprecated */
    @Deprecated
    public SequenceNode(Tag tag, boolean resolved, List value, Mark startMark, Mark endMark, Boolean style) {
        this(tag, resolved, value, startMark, endMark, DumperOptions.FlowStyle.fromBoolean(style));
    }

    public NodeId getNodeId() {
        return NodeId.sequence;
    }

    public List getValue() {
        return this.value;
    }

    public void setListType(Class listType) {
        Iterator i$ = this.value.iterator();

        while (i$.hasNext()) {
            Node node = (Node) i$.next();

            node.setType(listType);
        }

    }

    public String toString() {
        return "<" + this.getClass().getName() + " (tag=" + this.getTag() + ", value=" + this.getValue() + ")>";
    }
}
