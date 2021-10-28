package tech.mmmax.kami.api.value.builder;

import java.util.function.Consumer;
import tech.mmmax.kami.api.feature.Feature;
import tech.mmmax.kami.api.value.Value;

public class ValueBuilder {

    Value value = new Value();

    public ValueBuilder withDescriptor(String name, String tag) {
        this.value.setName(name);
        this.value.setTag(tag);
        return this;
    }

    public ValueBuilder withDescriptor(String name) {
        this.value.setName(name);
        String camelCase = name.replace(" ", "");
        char[] chars = camelCase.toCharArray();

        chars[0] = Character.toLowerCase(chars[0]);
        camelCase = new String(chars);
        this.value.setTag(camelCase);
        return this;
    }

    public ValueBuilder withValue(Object value) {
        this.value.setValue(value);
        return this;
    }

    public ValueBuilder withAction(Consumer action) {
        this.value.setAction(action);
        return this;
    }

    public ValueBuilder withRange(Object min, Object max) {
        this.value.setMin(min);
        this.value.setMax(max);
        return this;
    }

    public ValueBuilder withModes(String... modes) {
        this.value.setModes(modes);
        return this;
    }

    public Value getValue() {
        return this.value;
    }

    public Value register(Feature feature) {
        feature.getValues().add(this.value);
        return this.value;
    }
}
