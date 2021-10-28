package org.yaml.snakeyaml.nodes;

import java.util.List;
import org.yaml.snakeyaml.error.Mark;

public abstract class Node {

    private Tag tag;
    private Mark startMark;
    protected Mark endMark;
    private Class type;
    private boolean twoStepsConstruction;
    private String anchor;
    private List inLineComments;
    private List blockComments;
    private List endComments;
    protected boolean resolved;
    protected Boolean useClassConstructor;

    public Node(Tag tag, Mark startMark, Mark endMark) {
        this.setTag(tag);
        this.startMark = startMark;
        this.endMark = endMark;
        this.type = Object.class;
        this.twoStepsConstruction = false;
        this.resolved = true;
        this.useClassConstructor = null;
        this.inLineComments = null;
        this.blockComments = null;
        this.endComments = null;
    }

    public Tag getTag() {
        return this.tag;
    }

    public Mark getEndMark() {
        return this.endMark;
    }

    public abstract NodeId getNodeId();

    public Mark getStartMark() {
        return this.startMark;
    }

    public void setTag(Tag tag) {
        if (tag == null) {
            throw new NullPointerException("tag in a Node is required.");
        } else {
            this.tag = tag;
        }
    }

    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    public Class getType() {
        return this.type;
    }

    public void setType(Class type) {
        if (!type.isAssignableFrom(this.type)) {
            this.type = type;
        }

    }

    public void setTwoStepsConstruction(boolean twoStepsConstruction) {
        this.twoStepsConstruction = twoStepsConstruction;
    }

    public boolean isTwoStepsConstruction() {
        return this.twoStepsConstruction;
    }

    public final int hashCode() {
        return super.hashCode();
    }

    public boolean useClassConstructor() {
        return this.useClassConstructor == null ? (!this.tag.isSecondary() && this.resolved && !Object.class.equals(this.type) && !this.tag.equals(Tag.NULL) ? true : this.tag.isCompatible(this.getType())) : this.useClassConstructor.booleanValue();
    }

    public void setUseClassConstructor(Boolean useClassConstructor) {
        this.useClassConstructor = useClassConstructor;
    }

    /** @deprecated */
    @Deprecated
    public boolean isResolved() {
        return this.resolved;
    }

    public String getAnchor() {
        return this.anchor;
    }

    public void setAnchor(String anchor) {
        this.anchor = anchor;
    }

    public List getInLineComments() {
        return this.inLineComments;
    }

    public void setInLineComments(List inLineComments) {
        this.inLineComments = inLineComments;
    }

    public List getBlockComments() {
        return this.blockComments;
    }

    public void setBlockComments(List blockComments) {
        this.blockComments = blockComments;
    }

    public List getEndComments() {
        return this.endComments;
    }

    public void setEndComments(List endComments) {
        this.endComments = endComments;
    }
}
