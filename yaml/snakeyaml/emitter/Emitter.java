package org.yaml.snakeyaml.emitter;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.comments.CommentEventsCollector;
import org.yaml.snakeyaml.comments.CommentLine;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CollectionEndEvent;
import org.yaml.snakeyaml.events.CollectionStartEvent;
import org.yaml.snakeyaml.events.CommentEvent;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.NodeEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.scanner.Constant;
import org.yaml.snakeyaml.util.ArrayStack;

public final class Emitter implements Emitable {

    public static final int MIN_INDENT = 1;
    public static final int MAX_INDENT = 10;
    private static final char[] SPACE = new char[] { ' '};
    private static final Pattern SPACES_PATTERN = Pattern.compile("\\s");
    private static final Set INVALID_ANCHOR = new HashSet();
    private static final Map ESCAPE_REPLACEMENTS;
    private static final Map DEFAULT_TAG_PREFIXES;
    private final Writer stream;
    private final ArrayStack states;
    private EmitterState state;
    private final Queue events;
    private Event event;
    private final ArrayStack indents;
    private Integer indent;
    private int flowLevel;
    private boolean rootContext;
    private boolean mappingContext;
    private boolean simpleKeyContext;
    private int column;
    private boolean whitespace;
    private boolean indention;
    private boolean openEnded;
    private final Boolean canonical;
    private final Boolean prettyFlow;
    private final boolean allowUnicode;
    private int bestIndent;
    private final int indicatorIndent;
    private final boolean indentWithIndicator;
    private int bestWidth;
    private final char[] bestLineBreak;
    private final boolean splitLines;
    private final int maxSimpleKeyLength;
    private Map tagPrefixes;
    private String preparedAnchor;
    private String preparedTag;
    private ScalarAnalysis analysis;
    private DumperOptions.ScalarStyle style;
    private final CommentEventsCollector blockCommentsCollector;
    private final CommentEventsCollector inlineCommentsCollector;
    private static final Pattern HANDLE_FORMAT;

    public Emitter(Writer stream, DumperOptions opts) {
        this.stream = stream;
        this.states = new ArrayStack(100);
        this.state = new Emitter.ExpectStreamStart((Emitter.SyntheticClass_1) null);
        this.events = new ArrayBlockingQueue(100);
        this.event = null;
        this.indents = new ArrayStack(10);
        this.indent = null;
        this.flowLevel = 0;
        this.mappingContext = false;
        this.simpleKeyContext = false;
        this.column = 0;
        this.whitespace = true;
        this.indention = true;
        this.openEnded = false;
        this.canonical = Boolean.valueOf(opts.isCanonical());
        this.prettyFlow = Boolean.valueOf(opts.isPrettyFlow());
        this.allowUnicode = opts.isAllowUnicode();
        this.bestIndent = 2;
        if (opts.getIndent() > 1 && opts.getIndent() < 10) {
            this.bestIndent = opts.getIndent();
        }

        this.indicatorIndent = opts.getIndicatorIndent();
        this.indentWithIndicator = opts.getIndentWithIndicator();
        this.bestWidth = 80;
        if (opts.getWidth() > this.bestIndent * 2) {
            this.bestWidth = opts.getWidth();
        }

        this.bestLineBreak = opts.getLineBreak().getString().toCharArray();
        this.splitLines = opts.getSplitLines();
        this.maxSimpleKeyLength = opts.getMaxSimpleKeyLength();
        this.tagPrefixes = new LinkedHashMap();
        this.preparedAnchor = null;
        this.preparedTag = null;
        this.analysis = null;
        this.style = null;
        this.blockCommentsCollector = new CommentEventsCollector(this.events, new CommentType[] { CommentType.BLANK_LINE, CommentType.BLOCK});
        this.inlineCommentsCollector = new CommentEventsCollector(this.events, new CommentType[] { CommentType.IN_LINE});
    }

    public void emit(Event event) throws IOException {
        this.events.add(event);

        while (!this.needMoreEvents()) {
            this.event = (Event) this.events.poll();
            this.state.expect();
            this.event = null;
        }

    }

    private boolean needMoreEvents() {
        if (this.events.isEmpty()) {
            return true;
        } else {
            Iterator iter = this.events.iterator();
            Event event = null;

            do {
                if (!iter.hasNext()) {
                    return true;
                }

                event = (Event) iter.next();
            } while (event instanceof CommentEvent);

            if (event instanceof DocumentStartEvent) {
                return this.needEvents(iter, 1);
            } else if (event instanceof SequenceStartEvent) {
                return this.needEvents(iter, 2);
            } else if (event instanceof MappingStartEvent) {
                return this.needEvents(iter, 3);
            } else if (event instanceof StreamStartEvent) {
                return this.needEvents(iter, 2);
            } else if (event instanceof StreamEndEvent) {
                return false;
            } else {
                return this.needEvents(iter, 1);
            }
        }
    }

    private boolean needEvents(Iterator iter, int count) {
        int level = 0;
        int actualCount = 0;

        while (iter.hasNext()) {
            Event event = (Event) iter.next();

            if (!(event instanceof CommentEvent)) {
                ++actualCount;
                if (!(event instanceof DocumentStartEvent) && !(event instanceof CollectionStartEvent)) {
                    if (!(event instanceof DocumentEndEvent) && !(event instanceof CollectionEndEvent)) {
                        if (event instanceof StreamEndEvent) {
                            level = -1;
                        } else if (event instanceof CommentEvent) {
                            ;
                        }
                    } else {
                        --level;
                    }
                } else {
                    ++level;
                }

                if (level < 0) {
                    return false;
                }
            }
        }

        return actualCount < count;
    }

    private void increaseIndent(boolean flow, boolean indentless) {
        this.indents.push(this.indent);
        if (this.indent == null) {
            if (flow) {
                this.indent = Integer.valueOf(this.bestIndent);
            } else {
                this.indent = Integer.valueOf(0);
            }
        } else if (!indentless) {
            this.indent = Integer.valueOf(this.indent.intValue() + this.bestIndent);
        }

    }

    private void expectNode(boolean root, boolean mapping, boolean simpleKey) throws IOException {
        this.rootContext = root;
        this.mappingContext = mapping;
        this.simpleKeyContext = simpleKey;
        if (this.event instanceof AliasEvent) {
            this.expectAlias();
        } else {
            if (!(this.event instanceof ScalarEvent) && !(this.event instanceof CollectionStartEvent)) {
                throw new EmitterException("expected NodeEvent, but got " + this.event);
            }

            this.processAnchor("&");
            this.processTag();
            if (this.event instanceof ScalarEvent) {
                this.expectScalar();
            } else if (this.event instanceof SequenceStartEvent) {
                if (this.flowLevel == 0 && !this.canonical.booleanValue() && !((SequenceStartEvent) this.event).isFlow() && !this.checkEmptySequence()) {
                    this.expectBlockSequence();
                } else {
                    this.expectFlowSequence();
                }
            } else if (this.flowLevel == 0 && !this.canonical.booleanValue() && !((MappingStartEvent) this.event).isFlow() && !this.checkEmptyMapping()) {
                this.expectBlockMapping();
            } else {
                this.expectFlowMapping();
            }
        }

    }

    private void expectAlias() throws IOException {
        if (!(this.event instanceof AliasEvent)) {
            throw new EmitterException("Alias must be provided");
        } else {
            this.processAnchor("*");
            this.state = (EmitterState) this.states.pop();
        }
    }

    private void expectScalar() throws IOException {
        this.increaseIndent(true, false);
        this.processScalar();
        this.indent = (Integer) this.indents.pop();
        this.state = (EmitterState) this.states.pop();
    }

    private void expectFlowSequence() throws IOException {
        this.writeIndicator("[", true, true, false);
        ++this.flowLevel;
        this.increaseIndent(true, false);
        if (this.prettyFlow.booleanValue()) {
            this.writeIndent();
        }

        this.state = new Emitter.ExpectFirstFlowSequenceItem((Emitter.SyntheticClass_1) null);
    }

    private void expectFlowMapping() throws IOException {
        this.writeIndicator("{", true, true, false);
        ++this.flowLevel;
        this.increaseIndent(true, false);
        if (this.prettyFlow.booleanValue()) {
            this.writeIndent();
        }

        this.state = new Emitter.ExpectFirstFlowMappingKey((Emitter.SyntheticClass_1) null);
    }

    private void expectBlockSequence() throws IOException {
        boolean indentless = this.mappingContext && !this.indention;

        this.increaseIndent(false, indentless);
        this.state = new Emitter.ExpectFirstBlockSequenceItem((Emitter.SyntheticClass_1) null);
    }

    private void expectBlockMapping() throws IOException {
        this.increaseIndent(false, false);
        this.state = new Emitter.ExpectFirstBlockMappingKey((Emitter.SyntheticClass_1) null);
    }

    private boolean isFoldedOrLiteral(Event event) {
        if (!event.is(Event.ID.Scalar)) {
            return false;
        } else {
            ScalarEvent scalarEvent = (ScalarEvent) event;
            DumperOptions.ScalarStyle style = scalarEvent.getScalarStyle();

            return style == DumperOptions.ScalarStyle.FOLDED || style == DumperOptions.ScalarStyle.LITERAL;
        }
    }

    private boolean checkEmptySequence() {
        return this.event instanceof SequenceStartEvent && !this.events.isEmpty() && this.events.peek() instanceof SequenceEndEvent;
    }

    private boolean checkEmptyMapping() {
        return this.event instanceof MappingStartEvent && !this.events.isEmpty() && this.events.peek() instanceof MappingEndEvent;
    }

    private boolean checkEmptyDocument() {
        if (this.event instanceof DocumentStartEvent && !this.events.isEmpty()) {
            Event event = (Event) this.events.peek();

            if (!(event instanceof ScalarEvent)) {
                return false;
            } else {
                ScalarEvent e = (ScalarEvent) event;

                return e.getAnchor() == null && e.getTag() == null && e.getImplicit() != null && e.getValue().length() == 0;
            }
        } else {
            return false;
        }
    }

    private boolean checkSimpleKey() {
        int length = 0;

        if (this.event instanceof NodeEvent && ((NodeEvent) this.event).getAnchor() != null) {
            if (this.preparedAnchor == null) {
                this.preparedAnchor = prepareAnchor(((NodeEvent) this.event).getAnchor());
            }

            length += this.preparedAnchor.length();
        }

        String tag = null;

        if (this.event instanceof ScalarEvent) {
            tag = ((ScalarEvent) this.event).getTag();
        } else if (this.event instanceof CollectionStartEvent) {
            tag = ((CollectionStartEvent) this.event).getTag();
        }

        if (tag != null) {
            if (this.preparedTag == null) {
                this.preparedTag = this.prepareTag(tag);
            }

            length += this.preparedTag.length();
        }

        if (this.event instanceof ScalarEvent) {
            if (this.analysis == null) {
                this.analysis = this.analyzeScalar(((ScalarEvent) this.event).getValue());
            }

            length += this.analysis.getScalar().length();
        }

        return length < this.maxSimpleKeyLength && (this.event instanceof AliasEvent || this.event instanceof ScalarEvent && !this.analysis.isEmpty() && !this.analysis.isMultiline() || this.checkEmptySequence() || this.checkEmptyMapping());
    }

    private void processAnchor(String indicator) throws IOException {
        NodeEvent ev = (NodeEvent) this.event;

        if (ev.getAnchor() == null) {
            this.preparedAnchor = null;
        } else {
            if (this.preparedAnchor == null) {
                this.preparedAnchor = prepareAnchor(ev.getAnchor());
            }

            this.writeIndicator(indicator + this.preparedAnchor, true, false, false);
            this.preparedAnchor = null;
        }
    }

    private void processTag() throws IOException {
        String tag = null;

        if (this.event instanceof ScalarEvent) {
            ScalarEvent ev = (ScalarEvent) this.event;

            tag = ev.getTag();
            if (this.style == null) {
                this.style = this.chooseScalarStyle();
            }

            if ((!this.canonical.booleanValue() || tag == null) && (this.style == null && ev.getImplicit().canOmitTagInPlainScalar() || this.style != null && ev.getImplicit().canOmitTagInNonPlainScalar())) {
                this.preparedTag = null;
                return;
            }

            if (ev.getImplicit().canOmitTagInPlainScalar() && tag == null) {
                tag = "!";
                this.preparedTag = null;
            }
        } else {
            CollectionStartEvent ev1 = (CollectionStartEvent) this.event;

            tag = ev1.getTag();
            if ((!this.canonical.booleanValue() || tag == null) && ev1.getImplicit()) {
                this.preparedTag = null;
                return;
            }
        }

        if (tag == null) {
            throw new EmitterException("tag is not specified");
        } else {
            if (this.preparedTag == null) {
                this.preparedTag = this.prepareTag(tag);
            }

            this.writeIndicator(this.preparedTag, true, false, false);
            this.preparedTag = null;
        }
    }

    private DumperOptions.ScalarStyle chooseScalarStyle() {
        ScalarEvent ev = (ScalarEvent) this.event;

        if (this.analysis == null) {
            this.analysis = this.analyzeScalar(ev.getValue());
        }

        return (ev.isPlain() || ev.getScalarStyle() != DumperOptions.ScalarStyle.DOUBLE_QUOTED) && !this.canonical.booleanValue() ? (ev.isPlain() && ev.getImplicit().canOmitTagInPlainScalar() && (!this.simpleKeyContext || !this.analysis.isEmpty() && !this.analysis.isMultiline()) && (this.flowLevel != 0 && this.analysis.isAllowFlowPlain() || this.flowLevel == 0 && this.analysis.isAllowBlockPlain()) ? null : (!ev.isPlain() && (ev.getScalarStyle() == DumperOptions.ScalarStyle.LITERAL || ev.getScalarStyle() == DumperOptions.ScalarStyle.FOLDED) && this.flowLevel == 0 && !this.simpleKeyContext && this.analysis.isAllowBlock() ? ev.getScalarStyle() : ((ev.isPlain() || ev.getScalarStyle() == DumperOptions.ScalarStyle.SINGLE_QUOTED) && this.analysis.isAllowSingleQuoted() && (!this.simpleKeyContext || !this.analysis.isMultiline()) ? DumperOptions.ScalarStyle.SINGLE_QUOTED : DumperOptions.ScalarStyle.DOUBLE_QUOTED))) : DumperOptions.ScalarStyle.DOUBLE_QUOTED;
    }

    private void processScalar() throws IOException {
        ScalarEvent ev = (ScalarEvent) this.event;

        if (this.analysis == null) {
            this.analysis = this.analyzeScalar(ev.getValue());
        }

        if (this.style == null) {
            this.style = this.chooseScalarStyle();
        }

        boolean split = !this.simpleKeyContext && this.splitLines;

        if (this.style == null) {
            this.writePlain(this.analysis.getScalar(), split);
        } else {
            switch (this.style) {
            case DOUBLE_QUOTED:
                this.writeDoubleQuoted(this.analysis.getScalar(), split);
                break;

            case SINGLE_QUOTED:
                this.writeSingleQuoted(this.analysis.getScalar(), split);
                break;

            case FOLDED:
                this.writeFolded(this.analysis.getScalar(), split);
                break;

            case LITERAL:
                this.writeLiteral(this.analysis.getScalar());
                break;

            default:
                throw new YAMLException("Unexpected style: " + this.style);
            }
        }

        this.analysis = null;
        this.style = null;
    }

    private String prepareVersion(DumperOptions.Version version) {
        if (version.major() != 1) {
            throw new EmitterException("unsupported YAML version: " + version);
        } else {
            return version.getRepresentation();
        }
    }

    private String prepareTagHandle(String handle) {
        if (handle.length() == 0) {
            throw new EmitterException("tag handle must not be empty");
        } else if (handle.charAt(0) == 33 && handle.charAt(handle.length() - 1) == 33) {
            if (!"!".equals(handle) && !Emitter.HANDLE_FORMAT.matcher(handle).matches()) {
                throw new EmitterException("invalid character in the tag handle: " + handle);
            } else {
                return handle;
            }
        } else {
            throw new EmitterException("tag handle must start and end with \'!\': " + handle);
        }
    }

    private String prepareTagPrefix(String prefix) {
        if (prefix.length() == 0) {
            throw new EmitterException("tag prefix must not be empty");
        } else {
            StringBuilder chunks = new StringBuilder();
            byte start = 0;
            int end = 0;

            if (prefix.charAt(0) == 33) {
                end = 1;
            }

            while (end < prefix.length()) {
                ++end;
            }

            if (start < end) {
                chunks.append(prefix, start, end);
            }

            return chunks.toString();
        }
    }

    private String prepareTag(String tag) {
        if (tag.length() == 0) {
            throw new EmitterException("tag must not be empty");
        } else if ("!".equals(tag)) {
            return tag;
        } else {
            String handle = null;
            String suffix = tag;
            Iterator end = this.tagPrefixes.keySet().iterator();

            String suffixText;

            while (end.hasNext()) {
                suffixText = (String) end.next();
                if (tag.startsWith(suffixText) && ("!".equals(suffixText) || suffixText.length() < tag.length())) {
                    handle = suffixText;
                }
            }

            if (handle != null) {
                suffix = tag.substring(handle.length());
                handle = (String) this.tagPrefixes.get(handle);
            }

            int end1 = suffix.length();

            suffixText = end1 > 0 ? suffix.substring(0, end1) : "";
            if (handle != null) {
                return handle + suffixText;
            } else {
                return "!<" + suffixText + ">";
            }
        }
    }

    static String prepareAnchor(String anchor) {
        if (anchor.length() == 0) {
            throw new EmitterException("anchor must not be empty");
        } else {
            Iterator matcher = Emitter.INVALID_ANCHOR.iterator();

            Character invalid;

            do {
                if (!matcher.hasNext()) {
                    Matcher matcher1 = Emitter.SPACES_PATTERN.matcher(anchor);

                    if (matcher1.find()) {
                        throw new EmitterException("Anchor may not contain spaces: " + anchor);
                    }

                    return anchor;
                }

                invalid = (Character) matcher.next();
            } while (anchor.indexOf(invalid.charValue()) <= -1);

            throw new EmitterException("Invalid character \'" + invalid + "\' in the anchor: " + anchor);
        }
    }

    private ScalarAnalysis analyzeScalar(String scalar) {
        if (scalar.length() == 0) {
            return new ScalarAnalysis(scalar, true, false, false, true, true, false);
        } else {
            boolean blockIndicators = false;
            boolean flowIndicators = false;
            boolean lineBreaks = false;
            boolean specialCharacters = false;
            boolean leadingSpace = false;
            boolean leadingBreak = false;
            boolean trailingSpace = false;
            boolean trailingBreak = false;
            boolean breakSpace = false;
            boolean spaceBreak = false;

            if (scalar.startsWith("---") || scalar.startsWith("...")) {
                blockIndicators = true;
                flowIndicators = true;
            }

            boolean preceededByWhitespace = true;
            boolean followedByWhitespace = scalar.length() == 1 || Constant.NULL_BL_T_LINEBR.has(scalar.codePointAt(1));
            boolean previousSpace = false;
            boolean previousBreak = false;
            int index = 0;

            boolean allowBlockPlain;

            while (index < scalar.length()) {
                int allowFlowPlain = scalar.codePointAt(index);

                if (index == 0) {
                    if ("#,[]{}&*!|>\'\"%@`".indexOf(allowFlowPlain) != -1) {
                        flowIndicators = true;
                        blockIndicators = true;
                    }

                    if (allowFlowPlain == 63 || allowFlowPlain == 58) {
                        flowIndicators = true;
                        if (followedByWhitespace) {
                            blockIndicators = true;
                        }
                    }

                    if (allowFlowPlain == 45 && followedByWhitespace) {
                        flowIndicators = true;
                        blockIndicators = true;
                    }
                } else {
                    if (",?[]{}".indexOf(allowFlowPlain) != -1) {
                        flowIndicators = true;
                    }

                    if (allowFlowPlain == 58) {
                        flowIndicators = true;
                        if (followedByWhitespace) {
                            blockIndicators = true;
                        }
                    }

                    if (allowFlowPlain == 35 && preceededByWhitespace) {
                        flowIndicators = true;
                        blockIndicators = true;
                    }
                }

                allowBlockPlain = Constant.LINEBR.has(allowFlowPlain);
                if (allowBlockPlain) {
                    lineBreaks = true;
                }

                if (allowFlowPlain != 10 && (32 > allowFlowPlain || allowFlowPlain > 126)) {
                    if (allowFlowPlain != 133 && (allowFlowPlain < 160 || allowFlowPlain > '\ud7ff') && (allowFlowPlain < '\ue000' || allowFlowPlain > '�') && (allowFlowPlain < 65536 || allowFlowPlain > 1114111)) {
                        specialCharacters = true;
                    } else if (!this.allowUnicode) {
                        specialCharacters = true;
                    }
                }

                if (allowFlowPlain == 32) {
                    if (index == 0) {
                        leadingSpace = true;
                    }

                    if (index == scalar.length() - 1) {
                        trailingSpace = true;
                    }

                    if (previousBreak) {
                        breakSpace = true;
                    }

                    previousSpace = true;
                    previousBreak = false;
                } else if (allowBlockPlain) {
                    if (index == 0) {
                        leadingBreak = true;
                    }

                    if (index == scalar.length() - 1) {
                        trailingBreak = true;
                    }

                    if (previousSpace) {
                        spaceBreak = true;
                    }

                    previousSpace = false;
                    previousBreak = true;
                } else {
                    previousSpace = false;
                    previousBreak = false;
                }

                index += Character.charCount(allowFlowPlain);
                preceededByWhitespace = Constant.NULL_BL_T.has(allowFlowPlain) || allowBlockPlain;
                followedByWhitespace = true;
                if (index + 1 < scalar.length()) {
                    int allowSingleQuoted = index + Character.charCount(scalar.codePointAt(index));

                    if (allowSingleQuoted < scalar.length()) {
                        followedByWhitespace = Constant.NULL_BL_T.has(scalar.codePointAt(allowSingleQuoted)) || allowBlockPlain;
                    }
                }
            }

            boolean allowFlowPlain1 = true;

            allowBlockPlain = true;
            boolean allowSingleQuoted1 = true;
            boolean allowBlock = true;

            if (leadingSpace || leadingBreak || trailingSpace || trailingBreak) {
                allowBlockPlain = false;
                allowFlowPlain1 = false;
            }

            if (trailingSpace) {
                allowBlock = false;
            }

            if (breakSpace) {
                allowSingleQuoted1 = false;
                allowBlockPlain = false;
                allowFlowPlain1 = false;
            }

            if (spaceBreak || specialCharacters) {
                allowBlock = false;
                allowSingleQuoted1 = false;
                allowBlockPlain = false;
                allowFlowPlain1 = false;
            }

            if (lineBreaks) {
                allowFlowPlain1 = false;
            }

            if (flowIndicators) {
                allowFlowPlain1 = false;
            }

            if (blockIndicators) {
                allowBlockPlain = false;
            }

            return new ScalarAnalysis(scalar, false, lineBreaks, allowFlowPlain1, allowBlockPlain, allowSingleQuoted1, allowBlock);
        }
    }

    void flushStream() throws IOException {
        this.stream.flush();
    }

    void writeStreamStart() {}

    void writeStreamEnd() throws IOException {
        this.flushStream();
    }

    void writeIndicator(String indicator, boolean needWhitespace, boolean whitespace, boolean indentation) throws IOException {
        if (!this.whitespace && needWhitespace) {
            ++this.column;
            this.stream.write(Emitter.SPACE);
        }

        this.whitespace = whitespace;
        this.indention = this.indention && indentation;
        this.column += indicator.length();
        this.openEnded = false;
        this.stream.write(indicator);
    }

    void writeIndent() throws IOException {
        int indent;

        if (this.indent != null) {
            indent = this.indent.intValue();
        } else {
            indent = 0;
        }

        if (!this.indention || this.column > indent || this.column == indent && !this.whitespace) {
            this.writeLineBreak((String) null);
        }

        this.writeWhitespace(indent - this.column);
    }

    private void writeWhitespace(int length) throws IOException {
        if (length > 0) {
            this.whitespace = true;
            char[] data = new char[length];

            for (int i = 0; i < data.length; ++i) {
                data[i] = 32;
            }

            this.column += length;
            this.stream.write(data);
        }
    }

    private void writeLineBreak(String data) throws IOException {
        this.whitespace = true;
        this.indention = true;
        this.column = 0;
        if (data == null) {
            this.stream.write(this.bestLineBreak);
        } else {
            this.stream.write(data);
        }

    }

    void writeVersionDirective(String versionText) throws IOException {
        this.stream.write("%YAML ");
        this.stream.write(versionText);
        this.writeLineBreak((String) null);
    }

    void writeTagDirective(String handleText, String prefixText) throws IOException {
        this.stream.write("%TAG ");
        this.stream.write(handleText);
        this.stream.write(Emitter.SPACE);
        this.stream.write(prefixText);
        this.writeLineBreak((String) null);
    }

    private void writeSingleQuoted(String text, boolean split) throws IOException {
        this.writeIndicator("\'", true, false, false);
        boolean spaces = false;
        boolean breaks = false;
        int start = 0;

        for (int end = 0; end <= text.length(); ++end) {
            char ch = 0;

            if (end < text.length()) {
                ch = text.charAt(end);
            }

            int len;

            if (spaces) {
                if (ch == 0 || ch != 32) {
                    if (start + 1 == end && this.column > this.bestWidth && split && start != 0 && end != text.length()) {
                        this.writeIndent();
                    } else {
                        len = end - start;
                        this.column += len;
                        this.stream.write(text, start, len);
                    }

                    start = end;
                }
            } else if (breaks) {
                if (ch == 0 || Constant.LINEBR.hasNo(ch)) {
                    if (text.charAt(start) == 10) {
                        this.writeLineBreak((String) null);
                    }

                    String s = text.substring(start, end);
                    char[] arr$ = s.toCharArray();
                    int len$ = arr$.length;

                    for (int i$ = 0; i$ < len$; ++i$) {
                        char br = arr$[i$];

                        if (br == 10) {
                            this.writeLineBreak((String) null);
                        } else {
                            this.writeLineBreak(String.valueOf(br));
                        }
                    }

                    this.writeIndent();
                    start = end;
                }
            } else if (Constant.LINEBR.has(ch, "\u0000 \'") && start < end) {
                len = end - start;
                this.column += len;
                this.stream.write(text, start, len);
                start = end;
            }

            if (ch == 39) {
                this.column += 2;
                this.stream.write("\'\'");
                start = end + 1;
            }

            if (ch != 0) {
                spaces = ch == 32;
                breaks = Constant.LINEBR.has(ch);
            }
        }

        this.writeIndicator("\'", false, false, false);
    }

    private void writeDoubleQuoted(String text, boolean split) throws IOException {
        this.writeIndicator("\"", true, false, false);
        int start = 0;

        for (int end = 0; end <= text.length(); ++end) {
            Character ch = null;

            if (end < text.length()) {
                ch = Character.valueOf(text.charAt(end));
            }

            String s;

            if (ch == null || "\"\\\u0085\u2028\u2029\ufeff".indexOf(ch.charValue()) != -1 || 32 > ch.charValue() || ch.charValue() > 126) {
                if (start < end) {
                    int data = end - start;

                    this.column += data;
                    this.stream.write(text, start, data);
                    start = end;
                }

                if (ch != null) {
                    if (Emitter.ESCAPE_REPLACEMENTS.containsKey(ch)) {
                        s = "\\" + (String) Emitter.ESCAPE_REPLACEMENTS.get(ch);
                    } else if (this.allowUnicode && StreamReader.isPrintable(ch.charValue())) {
                        s = String.valueOf(ch);
                    } else {
                        String s;

                        if (ch.charValue() <= 255) {
                            s = "0" + Integer.toString(ch.charValue(), 16);
                            s = "\\x" + s.substring(s.length() - 2);
                        } else if (ch.charValue() >= '\ud800' && ch.charValue() <= '\udbff') {
                            if (end + 1 < text.length()) {
                                ++end;
                                Character character = Character.valueOf(text.charAt(end));
                                String s1 = "000" + Long.toHexString((long) Character.toCodePoint(ch.charValue(), character.charValue()));

                                s = "\\U" + s1.substring(s1.length() - 8);
                            } else {
                                s = "000" + Integer.toString(ch.charValue(), 16);
                                s = "\\u" + s.substring(s.length() - 4);
                            }
                        } else {
                            s = "000" + Integer.toString(ch.charValue(), 16);
                            s = "\\u" + s.substring(s.length() - 4);
                        }
                    }

                    this.column += s.length();
                    this.stream.write(s);
                    start = end + 1;
                }
            }

            if (0 < end && end < text.length() - 1 && (ch.charValue() == 32 || start >= end) && this.column + (end - start) > this.bestWidth && split) {
                if (start >= end) {
                    s = "\\";
                } else {
                    s = text.substring(start, end) + "\\";
                }

                if (start < end) {
                    start = end;
                }

                this.column += s.length();
                this.stream.write(s);
                this.writeIndent();
                this.whitespace = false;
                this.indention = false;
                if (text.charAt(start) == 32) {
                    s = "\\";
                    this.column += s.length();
                    this.stream.write(s);
                }
            }
        }

        this.writeIndicator("\"", false, false, false);
    }

    private boolean writeCommentLines(List commentLines) throws IOException {
        int indentColumns = 0;
        boolean firstComment = true;
        boolean wroteComment = false;

        for (Iterator i$ = commentLines.iterator(); i$.hasNext(); wroteComment = true) {
            CommentLine commentLine = (CommentLine) i$.next();

            if (commentLine.getCommentType() != CommentType.BLANK_LINE) {
                if (firstComment) {
                    firstComment = false;
                    this.writeIndicator("#", commentLine.getCommentType() == CommentType.IN_LINE, false, false);
                    indentColumns = this.column > 0 ? this.column - 1 : 0;
                } else {
                    this.writeWhitespace(indentColumns);
                    this.writeIndicator("#", false, false, false);
                }

                this.stream.write(commentLine.getValue());
            }

            this.writeLineBreak((String) null);
        }

        return wroteComment;
    }

    private void writeBlockComment() throws IOException {
        if (!this.blockCommentsCollector.isEmpty()) {
            this.writeIndent();
        }

        this.writeCommentLines(this.blockCommentsCollector.consume());
    }

    private boolean writeInlineComments() throws IOException {
        return this.writeCommentLines(this.inlineCommentsCollector.consume());
    }

    private String determineBlockHints(String text) {
        StringBuilder hints = new StringBuilder();

        if (Constant.LINEBR.has(text.charAt(0), " ")) {
            hints.append(this.bestIndent);
        }

        char ch1 = text.charAt(text.length() - 1);

        if (Constant.LINEBR.hasNo(ch1)) {
            hints.append("-");
        } else if (text.length() == 1 || Constant.LINEBR.has(text.charAt(text.length() - 2))) {
            hints.append("+");
        }

        return hints.toString();
    }

    void writeFolded(String text, boolean split) throws IOException {
        String hints = this.determineBlockHints(text);

        this.writeIndicator(">" + hints, true, false, false);
        if (hints.length() > 0 && hints.charAt(hints.length() - 1) == 43) {
            this.openEnded = true;
        }

        if (!this.writeInlineComments()) {
            this.writeLineBreak((String) null);
        }

        boolean leadingSpace = true;
        boolean spaces = false;
        boolean breaks = true;
        int start = 0;

        for (int end = 0; end <= text.length(); ++end) {
            char ch = 0;

            if (end < text.length()) {
                ch = text.charAt(end);
            }

            if (breaks) {
                if (ch == 0 || Constant.LINEBR.hasNo(ch)) {
                    if (!leadingSpace && ch != 0 && ch != 32 && text.charAt(start) == 10) {
                        this.writeLineBreak((String) null);
                    }

                    leadingSpace = ch == 32;
                    String len = text.substring(start, end);
                    char[] arr$ = len.toCharArray();
                    int len$ = arr$.length;

                    for (int i$ = 0; i$ < len$; ++i$) {
                        char br = arr$[i$];

                        if (br == 10) {
                            this.writeLineBreak((String) null);
                        } else {
                            this.writeLineBreak(String.valueOf(br));
                        }
                    }

                    if (ch != 0) {
                        this.writeIndent();
                    }

                    start = end;
                }
            } else {
                int i;

                if (spaces) {
                    if (ch != 32) {
                        if (start + 1 == end && this.column > this.bestWidth && split) {
                            this.writeIndent();
                        } else {
                            i = end - start;
                            this.column += i;
                            this.stream.write(text, start, i);
                        }

                        start = end;
                    }
                } else if (Constant.LINEBR.has(ch, "\u0000 ")) {
                    i = end - start;
                    this.column += i;
                    this.stream.write(text, start, i);
                    if (ch == 0) {
                        this.writeLineBreak((String) null);
                    }

                    start = end;
                }
            }

            if (ch != 0) {
                breaks = Constant.LINEBR.has(ch);
                spaces = ch == 32;
            }
        }

    }

    void writeLiteral(String text) throws IOException {
        String hints = this.determineBlockHints(text);

        this.writeIndicator("|" + hints, true, false, false);
        if (hints.length() > 0 && hints.charAt(hints.length() - 1) == 43) {
            this.openEnded = true;
        }

        if (!this.writeInlineComments()) {
            this.writeLineBreak((String) null);
        }

        boolean breaks = true;
        int start = 0;

        for (int end = 0; end <= text.length(); ++end) {
            char ch = 0;

            if (end < text.length()) {
                ch = text.charAt(end);
            }

            if (breaks) {
                if (ch == 0 || Constant.LINEBR.hasNo(ch)) {
                    String data = text.substring(start, end);
                    char[] arr$ = data.toCharArray();
                    int len$ = arr$.length;

                    for (int i$ = 0; i$ < len$; ++i$) {
                        char br = arr$[i$];

                        if (br == 10) {
                            this.writeLineBreak((String) null);
                        } else {
                            this.writeLineBreak(String.valueOf(br));
                        }
                    }

                    if (ch != 0) {
                        this.writeIndent();
                    }

                    start = end;
                }
            } else if (ch == 0 || Constant.LINEBR.has(ch)) {
                this.stream.write(text, start, end - start);
                if (ch == 0) {
                    this.writeLineBreak((String) null);
                }

                start = end;
            }

            if (ch != 0) {
                breaks = Constant.LINEBR.has(ch);
            }
        }

    }

    void writePlain(String text, boolean split) throws IOException {
        if (this.rootContext) {
            this.openEnded = true;
        }

        if (text.length() != 0) {
            if (!this.whitespace) {
                ++this.column;
                this.stream.write(Emitter.SPACE);
            }

            this.whitespace = false;
            this.indention = false;
            boolean spaces = false;
            boolean breaks = false;
            int start = 0;

            for (int end = 0; end <= text.length(); ++end) {
                char ch = 0;

                if (end < text.length()) {
                    ch = text.charAt(end);
                }

                int len;

                if (spaces) {
                    if (ch != 32) {
                        if (start + 1 == end && this.column > this.bestWidth && split) {
                            this.writeIndent();
                            this.whitespace = false;
                            this.indention = false;
                        } else {
                            len = end - start;
                            this.column += len;
                            this.stream.write(text, start, len);
                        }

                        start = end;
                    }
                } else if (breaks) {
                    if (Constant.LINEBR.hasNo(ch)) {
                        if (text.charAt(start) == 10) {
                            this.writeLineBreak((String) null);
                        }

                        String s = text.substring(start, end);
                        char[] arr$ = s.toCharArray();
                        int len$ = arr$.length;

                        for (int i$ = 0; i$ < len$; ++i$) {
                            char br = arr$[i$];

                            if (br == 10) {
                                this.writeLineBreak((String) null);
                            } else {
                                this.writeLineBreak(String.valueOf(br));
                            }
                        }

                        this.writeIndent();
                        this.whitespace = false;
                        this.indention = false;
                        start = end;
                    }
                } else if (Constant.LINEBR.has(ch, "\u0000 ")) {
                    len = end - start;
                    this.column += len;
                    this.stream.write(text, start, len);
                    start = end;
                }

                if (ch != 0) {
                    spaces = ch == 32;
                    breaks = Constant.LINEBR.has(ch);
                }
            }

        }
    }

    static int access$2210(Emitter x0) {
        return x0.flowLevel--;
    }

    static {
        Emitter.INVALID_ANCHOR.add(Character.valueOf('['));
        Emitter.INVALID_ANCHOR.add(Character.valueOf(']'));
        Emitter.INVALID_ANCHOR.add(Character.valueOf('{'));
        Emitter.INVALID_ANCHOR.add(Character.valueOf('}'));
        Emitter.INVALID_ANCHOR.add(Character.valueOf(','));
        Emitter.INVALID_ANCHOR.add(Character.valueOf('*'));
        Emitter.INVALID_ANCHOR.add(Character.valueOf('&'));
        ESCAPE_REPLACEMENTS = new HashMap();
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\u0000'), "0");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\u0007'), "a");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\b'), "b");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\t'), "t");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\n'), "n");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\u000b'), "v");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\f'), "f");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\r'), "r");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\u001b'), "e");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\"'), "\"");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\\'), "\\");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\u0085'), "N");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf(' '), "_");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\u2028'), "L");
        Emitter.ESCAPE_REPLACEMENTS.put(Character.valueOf('\u2029'), "P");
        DEFAULT_TAG_PREFIXES = new LinkedHashMap();
        Emitter.DEFAULT_TAG_PREFIXES.put("!", "!");
        Emitter.DEFAULT_TAG_PREFIXES.put("tag:yaml.org,2002:", "!!");
        HANDLE_FORMAT = Pattern.compile("^![-_\\w]*!$");
    }

    static class SyntheticClass_1 {

    }

    private class ExpectBlockMappingValue implements EmitterState {

        private ExpectBlockMappingValue() {}

        public void expect() throws IOException {
            Emitter.this.writeIndent();
            Emitter.this.writeIndicator(":", true, false, true);
            Emitter.this.event = Emitter.this.inlineCommentsCollector.collectEventsAndPoll(Emitter.this.event);
            Emitter.this.writeInlineComments();
            Emitter.this.event = Emitter.this.blockCommentsCollector.collectEventsAndPoll(Emitter.this.event);
            Emitter.this.writeBlockComment();
            Emitter.this.states.push(Emitter.this.new ExpectBlockMappingKey(false));
            Emitter.this.expectNode(false, true, false);
            Emitter.this.inlineCommentsCollector.collectEvents(Emitter.this.event);
            Emitter.this.writeInlineComments();
        }

        ExpectBlockMappingValue(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectBlockMappingSimpleValue implements EmitterState {

        private ExpectBlockMappingSimpleValue() {}

        public void expect() throws IOException {
            Emitter.this.writeIndicator(":", false, false, false);
            Emitter.this.event = Emitter.this.inlineCommentsCollector.collectEventsAndPoll(Emitter.this.event);
            if (!Emitter.this.isFoldedOrLiteral(Emitter.this.event) && Emitter.this.writeInlineComments()) {
                Emitter.this.increaseIndent(true, false);
                Emitter.this.writeIndent();
                Emitter.this.indent = (Integer) Emitter.this.indents.pop();
            }

            Emitter.this.event = Emitter.this.blockCommentsCollector.collectEventsAndPoll(Emitter.this.event);
            if (!Emitter.this.blockCommentsCollector.isEmpty()) {
                Emitter.this.increaseIndent(true, false);
                Emitter.this.writeBlockComment();
                Emitter.this.writeIndent();
                Emitter.this.indent = (Integer) Emitter.this.indents.pop();
            }

            Emitter.this.states.push(Emitter.this.new ExpectBlockMappingKey(false));
            Emitter.this.expectNode(false, true, false);
            Emitter.this.inlineCommentsCollector.collectEvents();
            Emitter.this.writeInlineComments();
        }

        ExpectBlockMappingSimpleValue(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectBlockMappingKey implements EmitterState {

        private final boolean first;

        public ExpectBlockMappingKey(boolean first) {
            this.first = first;
        }

        public void expect() throws IOException {
            Emitter.this.event = Emitter.this.blockCommentsCollector.collectEventsAndPoll(Emitter.this.event);
            Emitter.this.writeBlockComment();
            if (!this.first && Emitter.this.event instanceof MappingEndEvent) {
                Emitter.this.indent = (Integer) Emitter.this.indents.pop();
                Emitter.this.state = (EmitterState) Emitter.this.states.pop();
            } else {
                Emitter.this.writeIndent();
                if (Emitter.this.checkSimpleKey()) {
                    Emitter.this.states.push(Emitter.this.new ExpectBlockMappingSimpleValue((Emitter.SyntheticClass_1) null));
                    Emitter.this.expectNode(false, true, true);
                } else {
                    Emitter.this.writeIndicator("?", true, false, true);
                    Emitter.this.states.push(Emitter.this.new ExpectBlockMappingValue((Emitter.SyntheticClass_1) null));
                    Emitter.this.expectNode(false, true, false);
                }
            }

        }
    }

    private class ExpectFirstBlockMappingKey implements EmitterState {

        private ExpectFirstBlockMappingKey() {}

        public void expect() throws IOException {
            (Emitter.this.new ExpectBlockMappingKey(true)).expect();
        }

        ExpectFirstBlockMappingKey(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectBlockSequenceItem implements EmitterState {

        private final boolean first;

        public ExpectBlockSequenceItem(boolean first) {
            this.first = first;
        }

        public void expect() throws IOException {
            if (!this.first && Emitter.this.event instanceof SequenceEndEvent) {
                Emitter.this.indent = (Integer) Emitter.this.indents.pop();
                Emitter.this.state = (EmitterState) Emitter.this.states.pop();
            } else if (Emitter.this.event instanceof CommentEvent) {
                Emitter.this.blockCommentsCollector.collectEvents(Emitter.this.event);
                Emitter.this.writeBlockComment();
            } else {
                Emitter.this.writeIndent();
                if (!Emitter.this.indentWithIndicator || this.first) {
                    Emitter.this.writeWhitespace(Emitter.this.indicatorIndent);
                }

                Emitter.this.writeIndicator("-", true, false, true);
                if (Emitter.this.indentWithIndicator && this.first) {
                    Emitter.this.indent = Integer.valueOf(Emitter.this.indent.intValue() + Emitter.this.indicatorIndent);
                }

                Emitter.this.states.push(Emitter.this.new ExpectBlockSequenceItem(false));
                Emitter.this.expectNode(false, false, false);
                Emitter.this.inlineCommentsCollector.collectEvents();
                Emitter.this.writeInlineComments();
            }

        }
    }

    private class ExpectFirstBlockSequenceItem implements EmitterState {

        private ExpectFirstBlockSequenceItem() {}

        public void expect() throws IOException {
            (Emitter.this.new ExpectBlockSequenceItem(true)).expect();
        }

        ExpectFirstBlockSequenceItem(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectFlowMappingValue implements EmitterState {

        private ExpectFlowMappingValue() {}

        public void expect() throws IOException {
            if (Emitter.this.canonical.booleanValue() || Emitter.this.column > Emitter.this.bestWidth || Emitter.this.prettyFlow.booleanValue()) {
                Emitter.this.writeIndent();
            }

            Emitter.this.writeIndicator(":", true, false, false);
            Emitter.this.event = Emitter.this.inlineCommentsCollector.collectEventsAndPoll(Emitter.this.event);
            Emitter.this.writeInlineComments();
            Emitter.this.states.push(Emitter.this.new ExpectFlowMappingKey((Emitter.SyntheticClass_1) null));
            Emitter.this.expectNode(false, true, false);
            Emitter.this.inlineCommentsCollector.collectEvents(Emitter.this.event);
            Emitter.this.writeInlineComments();
        }

        ExpectFlowMappingValue(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectFlowMappingSimpleValue implements EmitterState {

        private ExpectFlowMappingSimpleValue() {}

        public void expect() throws IOException {
            Emitter.this.writeIndicator(":", false, false, false);
            Emitter.this.event = Emitter.this.inlineCommentsCollector.collectEventsAndPoll(Emitter.this.event);
            Emitter.this.writeInlineComments();
            Emitter.this.states.push(Emitter.this.new ExpectFlowMappingKey((Emitter.SyntheticClass_1) null));
            Emitter.this.expectNode(false, true, false);
            Emitter.this.inlineCommentsCollector.collectEvents(Emitter.this.event);
            Emitter.this.writeInlineComments();
        }

        ExpectFlowMappingSimpleValue(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectFlowMappingKey implements EmitterState {

        private ExpectFlowMappingKey() {}

        public void expect() throws IOException {
            if (Emitter.this.event instanceof MappingEndEvent) {
                Emitter.this.indent = (Integer) Emitter.this.indents.pop();
                Emitter.access$2210(Emitter.this);
                if (Emitter.this.canonical.booleanValue()) {
                    Emitter.this.writeIndicator(",", false, false, false);
                    Emitter.this.writeIndent();
                }

                if (Emitter.this.prettyFlow.booleanValue()) {
                    Emitter.this.writeIndent();
                }

                Emitter.this.writeIndicator("}", false, false, false);
                Emitter.this.inlineCommentsCollector.collectEvents();
                Emitter.this.writeInlineComments();
                Emitter.this.state = (EmitterState) Emitter.this.states.pop();
            } else {
                Emitter.this.writeIndicator(",", false, false, false);
                if (Emitter.this.canonical.booleanValue() || Emitter.this.column > Emitter.this.bestWidth && Emitter.this.splitLines || Emitter.this.prettyFlow.booleanValue()) {
                    Emitter.this.writeIndent();
                }

                if (!Emitter.this.canonical.booleanValue() && Emitter.this.checkSimpleKey()) {
                    Emitter.this.states.push(Emitter.this.new ExpectFlowMappingSimpleValue((Emitter.SyntheticClass_1) null));
                    Emitter.this.expectNode(false, true, true);
                } else {
                    Emitter.this.writeIndicator("?", true, false, false);
                    Emitter.this.states.push(Emitter.this.new ExpectFlowMappingValue((Emitter.SyntheticClass_1) null));
                    Emitter.this.expectNode(false, true, false);
                }
            }

        }

        ExpectFlowMappingKey(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectFirstFlowMappingKey implements EmitterState {

        private ExpectFirstFlowMappingKey() {}

        public void expect() throws IOException {
            if (Emitter.this.event instanceof MappingEndEvent) {
                Emitter.this.indent = (Integer) Emitter.this.indents.pop();
                Emitter.access$2210(Emitter.this);
                Emitter.this.writeIndicator("}", false, false, false);
                Emitter.this.inlineCommentsCollector.collectEvents();
                Emitter.this.writeInlineComments();
                Emitter.this.state = (EmitterState) Emitter.this.states.pop();
            } else {
                if (Emitter.this.canonical.booleanValue() || Emitter.this.column > Emitter.this.bestWidth && Emitter.this.splitLines || Emitter.this.prettyFlow.booleanValue()) {
                    Emitter.this.writeIndent();
                }

                if (!Emitter.this.canonical.booleanValue() && Emitter.this.checkSimpleKey()) {
                    Emitter.this.states.push(Emitter.this.new ExpectFlowMappingSimpleValue((Emitter.SyntheticClass_1) null));
                    Emitter.this.expectNode(false, true, true);
                } else {
                    Emitter.this.writeIndicator("?", true, false, false);
                    Emitter.this.states.push(Emitter.this.new ExpectFlowMappingValue((Emitter.SyntheticClass_1) null));
                    Emitter.this.expectNode(false, true, false);
                }
            }

        }

        ExpectFirstFlowMappingKey(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectFlowSequenceItem implements EmitterState {

        private ExpectFlowSequenceItem() {}

        public void expect() throws IOException {
            if (Emitter.this.event instanceof SequenceEndEvent) {
                Emitter.this.indent = (Integer) Emitter.this.indents.pop();
                Emitter.access$2210(Emitter.this);
                if (Emitter.this.canonical.booleanValue()) {
                    Emitter.this.writeIndicator(",", false, false, false);
                    Emitter.this.writeIndent();
                }

                Emitter.this.writeIndicator("]", false, false, false);
                Emitter.this.inlineCommentsCollector.collectEvents();
                Emitter.this.writeInlineComments();
                if (Emitter.this.prettyFlow.booleanValue()) {
                    Emitter.this.writeIndent();
                }

                Emitter.this.state = (EmitterState) Emitter.this.states.pop();
            } else if (Emitter.this.event instanceof CommentEvent) {
                Emitter.this.blockCommentsCollector.collectEvents(Emitter.this.event);
                Emitter.this.writeBlockComment();
            } else {
                Emitter.this.writeIndicator(",", false, false, false);
                if (Emitter.this.canonical.booleanValue() || Emitter.this.column > Emitter.this.bestWidth && Emitter.this.splitLines || Emitter.this.prettyFlow.booleanValue()) {
                    Emitter.this.writeIndent();
                }

                Emitter.this.states.push(Emitter.this.new ExpectFlowSequenceItem());
                Emitter.this.expectNode(false, false, false);
                Emitter.this.inlineCommentsCollector.collectEvents(Emitter.this.event);
                Emitter.this.writeInlineComments();
            }

        }

        ExpectFlowSequenceItem(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectFirstFlowSequenceItem implements EmitterState {

        private ExpectFirstFlowSequenceItem() {}

        public void expect() throws IOException {
            if (Emitter.this.event instanceof SequenceEndEvent) {
                Emitter.this.indent = (Integer) Emitter.this.indents.pop();
                Emitter.access$2210(Emitter.this);
                Emitter.this.writeIndicator("]", false, false, false);
                Emitter.this.inlineCommentsCollector.collectEvents();
                Emitter.this.writeInlineComments();
                Emitter.this.state = (EmitterState) Emitter.this.states.pop();
            } else if (Emitter.this.event instanceof CommentEvent) {
                Emitter.this.blockCommentsCollector.collectEvents(Emitter.this.event);
                Emitter.this.writeBlockComment();
            } else {
                if (Emitter.this.canonical.booleanValue() || Emitter.this.column > Emitter.this.bestWidth && Emitter.this.splitLines || Emitter.this.prettyFlow.booleanValue()) {
                    Emitter.this.writeIndent();
                }

                Emitter.this.states.push(Emitter.this.new ExpectFlowSequenceItem((Emitter.SyntheticClass_1) null));
                Emitter.this.expectNode(false, false, false);
                Emitter.this.event = Emitter.this.inlineCommentsCollector.collectEvents(Emitter.this.event);
                Emitter.this.writeInlineComments();
            }

        }

        ExpectFirstFlowSequenceItem(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectDocumentRoot implements EmitterState {

        private ExpectDocumentRoot() {}

        public void expect() throws IOException {
            Emitter.this.event = Emitter.this.blockCommentsCollector.collectEventsAndPoll(Emitter.this.event);
            if (!Emitter.this.blockCommentsCollector.isEmpty()) {
                Emitter.this.writeBlockComment();
                if (Emitter.this.event instanceof DocumentEndEvent) {
                    (Emitter.this.new ExpectDocumentEnd((Emitter.SyntheticClass_1) null)).expect();
                    return;
                }
            }

            Emitter.this.states.push(Emitter.this.new ExpectDocumentEnd((Emitter.SyntheticClass_1) null));
            Emitter.this.expectNode(true, false, false);
        }

        ExpectDocumentRoot(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectDocumentEnd implements EmitterState {

        private ExpectDocumentEnd() {}

        public void expect() throws IOException {
            Emitter.this.event = Emitter.this.blockCommentsCollector.collectEventsAndPoll(Emitter.this.event);
            Emitter.this.writeBlockComment();
            if (Emitter.this.event instanceof DocumentEndEvent) {
                Emitter.this.writeIndent();
                if (((DocumentEndEvent) Emitter.this.event).getExplicit()) {
                    Emitter.this.writeIndicator("...", true, false, false);
                    Emitter.this.writeIndent();
                }

                Emitter.this.flushStream();
                Emitter.this.state = Emitter.this.new ExpectDocumentStart(false);
            } else {
                throw new EmitterException("expected DocumentEndEvent, but got " + Emitter.this.event);
            }
        }

        ExpectDocumentEnd(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectDocumentStart implements EmitterState {

        private final boolean first;

        public ExpectDocumentStart(boolean first) {
            this.first = first;
        }

        public void expect() throws IOException {
            if (Emitter.this.event instanceof DocumentStartEvent) {
                DocumentStartEvent ev = (DocumentStartEvent) Emitter.this.event;

                if ((ev.getVersion() != null || ev.getTags() != null) && Emitter.this.openEnded) {
                    Emitter.this.writeIndicator("...", true, false, false);
                    Emitter.this.writeIndent();
                }

                if (ev.getVersion() != null) {
                    String implicit = Emitter.this.prepareVersion(ev.getVersion());

                    Emitter.this.writeVersionDirective(implicit);
                }

                Emitter.this.tagPrefixes = new LinkedHashMap(Emitter.DEFAULT_TAG_PREFIXES);
                if (ev.getTags() != null) {
                    TreeSet implicit1 = new TreeSet(ev.getTags().keySet());
                    Iterator i$ = implicit1.iterator();

                    while (i$.hasNext()) {
                        String handle = (String) i$.next();
                        String prefix = (String) ev.getTags().get(handle);

                        Emitter.this.tagPrefixes.put(prefix, handle);
                        String handleText = Emitter.this.prepareTagHandle(handle);
                        String prefixText = Emitter.this.prepareTagPrefix(prefix);

                        Emitter.this.writeTagDirective(handleText, prefixText);
                    }
                }

                boolean implicit2 = this.first && !ev.getExplicit() && !Emitter.this.canonical.booleanValue() && ev.getVersion() == null && (ev.getTags() == null || ev.getTags().isEmpty()) && !Emitter.this.checkEmptyDocument();

                if (!implicit2) {
                    Emitter.this.writeIndent();
                    Emitter.this.writeIndicator("---", true, false, false);
                    if (Emitter.this.canonical.booleanValue()) {
                        Emitter.this.writeIndent();
                    }
                }

                Emitter.this.state = Emitter.this.new ExpectDocumentRoot((Emitter.SyntheticClass_1) null);
            } else if (Emitter.this.event instanceof StreamEndEvent) {
                Emitter.this.writeStreamEnd();
                Emitter.this.state = Emitter.this.new ExpectNothing((Emitter.SyntheticClass_1) null);
            } else {
                if (!(Emitter.this.event instanceof CommentEvent)) {
                    throw new EmitterException("expected DocumentStartEvent, but got " + Emitter.this.event);
                }

                Emitter.this.blockCommentsCollector.collectEvents(Emitter.this.event);
                Emitter.this.writeBlockComment();
            }

        }
    }

    private class ExpectFirstDocumentStart implements EmitterState {

        private ExpectFirstDocumentStart() {}

        public void expect() throws IOException {
            (Emitter.this.new ExpectDocumentStart(true)).expect();
        }

        ExpectFirstDocumentStart(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectNothing implements EmitterState {

        private ExpectNothing() {}

        public void expect() throws IOException {
            throw new EmitterException("expecting nothing, but got " + Emitter.this.event);
        }

        ExpectNothing(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ExpectStreamStart implements EmitterState {

        private ExpectStreamStart() {}

        public void expect() throws IOException {
            if (Emitter.this.event instanceof StreamStartEvent) {
                Emitter.this.writeStreamStart();
                Emitter.this.state = Emitter.this.new ExpectFirstDocumentStart((Emitter.SyntheticClass_1) null);
            } else {
                throw new EmitterException("expected StreamStartEvent, but got " + Emitter.this.event);
            }
        }

        ExpectStreamStart(Emitter.SyntheticClass_1 x1) {
            this();
        }
    }
}
