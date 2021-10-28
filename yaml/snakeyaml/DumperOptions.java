package org.yaml.snakeyaml;

import java.util.Map;
import java.util.TimeZone;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.serializer.AnchorGenerator;
import org.yaml.snakeyaml.serializer.NumberAnchorGenerator;

public class DumperOptions {

    private DumperOptions.ScalarStyle defaultStyle;
    private DumperOptions.FlowStyle defaultFlowStyle;
    private boolean canonical;
    private boolean allowUnicode;
    private boolean allowReadOnlyProperties;
    private int indent;
    private int indicatorIndent;
    private boolean indentWithIndicator;
    private int bestWidth;
    private boolean splitLines;
    private DumperOptions.LineBreak lineBreak;
    private boolean explicitStart;
    private boolean explicitEnd;
    private TimeZone timeZone;
    private int maxSimpleKeyLength;
    private DumperOptions.NonPrintableStyle nonPrintableStyle;
    private DumperOptions.Version version;
    private Map tags;
    private Boolean prettyFlow;
    private AnchorGenerator anchorGenerator;

    public DumperOptions() {
        this.defaultStyle = DumperOptions.ScalarStyle.PLAIN;
        this.defaultFlowStyle = DumperOptions.FlowStyle.AUTO;
        this.canonical = false;
        this.allowUnicode = true;
        this.allowReadOnlyProperties = false;
        this.indent = 2;
        this.indicatorIndent = 0;
        this.indentWithIndicator = false;
        this.bestWidth = 80;
        this.splitLines = true;
        this.lineBreak = DumperOptions.LineBreak.UNIX;
        this.explicitStart = false;
        this.explicitEnd = false;
        this.timeZone = null;
        this.maxSimpleKeyLength = 128;
        this.nonPrintableStyle = DumperOptions.NonPrintableStyle.BINARY;
        this.version = null;
        this.tags = null;
        this.prettyFlow = Boolean.valueOf(false);
        this.anchorGenerator = new NumberAnchorGenerator(0);
    }

    public boolean isAllowUnicode() {
        return this.allowUnicode;
    }

    public void setAllowUnicode(boolean allowUnicode) {
        this.allowUnicode = allowUnicode;
    }

    public DumperOptions.ScalarStyle getDefaultScalarStyle() {
        return this.defaultStyle;
    }

    public void setDefaultScalarStyle(DumperOptions.ScalarStyle defaultStyle) {
        if (defaultStyle == null) {
            throw new NullPointerException("Use ScalarStyle enum.");
        } else {
            this.defaultStyle = defaultStyle;
        }
    }

    public void setIndent(int indent) {
        if (indent < 1) {
            throw new YAMLException("Indent must be at least 1");
        } else if (indent > 10) {
            throw new YAMLException("Indent must be at most 10");
        } else {
            this.indent = indent;
        }
    }

    public int getIndent() {
        return this.indent;
    }

    public void setIndicatorIndent(int indicatorIndent) {
        if (indicatorIndent < 0) {
            throw new YAMLException("Indicator indent must be non-negative.");
        } else if (indicatorIndent > 9) {
            throw new YAMLException("Indicator indent must be at most Emitter.MAX_INDENT-1: 9");
        } else {
            this.indicatorIndent = indicatorIndent;
        }
    }

    public int getIndicatorIndent() {
        return this.indicatorIndent;
    }

    public boolean getIndentWithIndicator() {
        return this.indentWithIndicator;
    }

    public void setIndentWithIndicator(boolean indentWithIndicator) {
        this.indentWithIndicator = indentWithIndicator;
    }

    public void setVersion(DumperOptions.Version version) {
        this.version = version;
    }

    public DumperOptions.Version getVersion() {
        return this.version;
    }

    public void setCanonical(boolean canonical) {
        this.canonical = canonical;
    }

    public boolean isCanonical() {
        return this.canonical;
    }

    public void setPrettyFlow(boolean prettyFlow) {
        this.prettyFlow = Boolean.valueOf(prettyFlow);
    }

    public boolean isPrettyFlow() {
        return this.prettyFlow.booleanValue();
    }

    public void setWidth(int bestWidth) {
        this.bestWidth = bestWidth;
    }

    public int getWidth() {
        return this.bestWidth;
    }

    public void setSplitLines(boolean splitLines) {
        this.splitLines = splitLines;
    }

    public boolean getSplitLines() {
        return this.splitLines;
    }

    public DumperOptions.LineBreak getLineBreak() {
        return this.lineBreak;
    }

    public void setDefaultFlowStyle(DumperOptions.FlowStyle defaultFlowStyle) {
        if (defaultFlowStyle == null) {
            throw new NullPointerException("Use FlowStyle enum.");
        } else {
            this.defaultFlowStyle = defaultFlowStyle;
        }
    }

    public DumperOptions.FlowStyle getDefaultFlowStyle() {
        return this.defaultFlowStyle;
    }

    public void setLineBreak(DumperOptions.LineBreak lineBreak) {
        if (lineBreak == null) {
            throw new NullPointerException("Specify line break.");
        } else {
            this.lineBreak = lineBreak;
        }
    }

    public boolean isExplicitStart() {
        return this.explicitStart;
    }

    public void setExplicitStart(boolean explicitStart) {
        this.explicitStart = explicitStart;
    }

    public boolean isExplicitEnd() {
        return this.explicitEnd;
    }

    public void setExplicitEnd(boolean explicitEnd) {
        this.explicitEnd = explicitEnd;
    }

    public Map getTags() {
        return this.tags;
    }

    public void setTags(Map tags) {
        this.tags = tags;
    }

    public boolean isAllowReadOnlyProperties() {
        return this.allowReadOnlyProperties;
    }

    public void setAllowReadOnlyProperties(boolean allowReadOnlyProperties) {
        this.allowReadOnlyProperties = allowReadOnlyProperties;
    }

    public TimeZone getTimeZone() {
        return this.timeZone;
    }

    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    public AnchorGenerator getAnchorGenerator() {
        return this.anchorGenerator;
    }

    public void setAnchorGenerator(AnchorGenerator anchorGenerator) {
        this.anchorGenerator = anchorGenerator;
    }

    public int getMaxSimpleKeyLength() {
        return this.maxSimpleKeyLength;
    }

    public void setMaxSimpleKeyLength(int maxSimpleKeyLength) {
        if (maxSimpleKeyLength > 1024) {
            throw new YAMLException("The simple key must not span more than 1024 stream characters. See https://yaml.org/spec/1.1/#id934537");
        } else {
            this.maxSimpleKeyLength = maxSimpleKeyLength;
        }
    }

    public DumperOptions.NonPrintableStyle getNonPrintableStyle() {
        return this.nonPrintableStyle;
    }

    public void setNonPrintableStyle(DumperOptions.NonPrintableStyle style) {
        this.nonPrintableStyle = style;
    }

    public static enum NonPrintableStyle {

        BINARY, ESCAPE;
    }

    public static enum Version {

        V1_0(new Integer[] { Integer.valueOf(1), Integer.valueOf(0)}), V1_1(new Integer[] { Integer.valueOf(1), Integer.valueOf(1)});

        private Integer[] version;

        private Version(Integer[] version) {
            this.version = version;
        }

        public int major() {
            return this.version[0].intValue();
        }

        public int minor() {
            return this.version[1].intValue();
        }

        public String getRepresentation() {
            return this.version[0] + "." + this.version[1];
        }

        public String toString() {
            return "Version: " + this.getRepresentation();
        }
    }

    public static enum LineBreak {

        WIN("\r\n"), MAC("\r"), UNIX("\n");

        private String lineBreak;

        private LineBreak(String lineBreak) {
            this.lineBreak = lineBreak;
        }

        public String getString() {
            return this.lineBreak;
        }

        public String toString() {
            return "Line break: " + this.name();
        }

        public static DumperOptions.LineBreak getPlatformLineBreak() {
            String platformLineBreak = System.getProperty("line.separator");
            DumperOptions.LineBreak[] arr$ = values();
            int len$ = arr$.length;

            for (int i$ = 0; i$ < len$; ++i$) {
                DumperOptions.LineBreak lb = arr$[i$];

                if (lb.lineBreak.equals(platformLineBreak)) {
                    return lb;
                }
            }

            return DumperOptions.LineBreak.UNIX;
        }
    }

    public static enum FlowStyle {

        FLOW(Boolean.TRUE), BLOCK(Boolean.FALSE), AUTO((Boolean) null);

        private Boolean styleBoolean;

        private FlowStyle(Boolean flowStyle) {
            this.styleBoolean = flowStyle;
        }

        /** @deprecated */
        @Deprecated
        public static DumperOptions.FlowStyle fromBoolean(Boolean flowStyle) {
            return flowStyle == null ? DumperOptions.FlowStyle.AUTO : (flowStyle.booleanValue() ? DumperOptions.FlowStyle.FLOW : DumperOptions.FlowStyle.BLOCK);
        }

        public Boolean getStyleBoolean() {
            return this.styleBoolean;
        }

        public String toString() {
            return "Flow style: \'" + this.styleBoolean + "\'";
        }
    }

    public static enum ScalarStyle {

        DOUBLE_QUOTED(Character.valueOf('\"')), SINGLE_QUOTED(Character.valueOf('\'')), LITERAL(Character.valueOf('|')), FOLDED(Character.valueOf('>')), PLAIN((Character) null);

        private Character styleChar;

        private ScalarStyle(Character style) {
            this.styleChar = style;
        }

        public Character getChar() {
            return this.styleChar;
        }

        public String toString() {
            return "Scalar style: \'" + this.styleChar + "\'";
        }

        public static DumperOptions.ScalarStyle createStyle(Character style) {
            if (style == null) {
                return DumperOptions.ScalarStyle.PLAIN;
            } else {
                switch (style.charValue()) {
                case '\"':
                    return DumperOptions.ScalarStyle.DOUBLE_QUOTED;

                case '\'':
                    return DumperOptions.ScalarStyle.SINGLE_QUOTED;

                case '>':
                    return DumperOptions.ScalarStyle.FOLDED;

                case '|':
                    return DumperOptions.ScalarStyle.LITERAL;

                default:
                    throw new YAMLException("Unknown scalar style character: " + style);
                }
            }
        }
    }
}
