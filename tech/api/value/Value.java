package tech.mmmax.kami.api.value;

import java.util.function.Consumer;

public class Value {

    String name;
    String tag;
    Object min;
    Object max;
    String[] modes;
    boolean active = true;
    Consumer action;
    boolean enabled;
    Object value;

    public Object getValue() {
        return this.value;
    }

    public String getName() {
        return this.name;
    }

    public String getTag() {
        return this.tag;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public void setValue(Object value) {
        this.value = value;
        if (this.action != null) {
            this.action.accept(this);
        }

    }

    public Object getMin() {
        return this.min;
    }

    public Object getMax() {
        return this.max;
    }

    public void setMin(Object min) {
        this.min = min;
    }

    public void setMax(Object max) {
        this.max = max;
    }

    public String[] getModes() {
        return this.modes;
    }

    public void setModes(String[] modes) {
        this.modes = modes;
    }

    public Consumer getAction() {
        return this.action;
    }

    public void setAction(Consumer action) {
        this.action = action;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
