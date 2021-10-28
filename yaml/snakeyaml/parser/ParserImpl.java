package org.yaml.snakeyaml.parser;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.comments.CommentType;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.events.AliasEvent;
import org.yaml.snakeyaml.events.CommentEvent;
import org.yaml.snakeyaml.events.DocumentEndEvent;
import org.yaml.snakeyaml.events.DocumentStartEvent;
import org.yaml.snakeyaml.events.Event;
import org.yaml.snakeyaml.events.ImplicitTuple;
import org.yaml.snakeyaml.events.MappingEndEvent;
import org.yaml.snakeyaml.events.MappingStartEvent;
import org.yaml.snakeyaml.events.ScalarEvent;
import org.yaml.snakeyaml.events.SequenceEndEvent;
import org.yaml.snakeyaml.events.SequenceStartEvent;
import org.yaml.snakeyaml.events.StreamEndEvent;
import org.yaml.snakeyaml.events.StreamStartEvent;
import org.yaml.snakeyaml.reader.StreamReader;
import org.yaml.snakeyaml.scanner.Scanner;
import org.yaml.snakeyaml.scanner.ScannerImpl;
import org.yaml.snakeyaml.tokens.AliasToken;
import org.yaml.snakeyaml.tokens.AnchorToken;
import org.yaml.snakeyaml.tokens.BlockEntryToken;
import org.yaml.snakeyaml.tokens.CommentToken;
import org.yaml.snakeyaml.tokens.DirectiveToken;
import org.yaml.snakeyaml.tokens.ScalarToken;
import org.yaml.snakeyaml.tokens.StreamEndToken;
import org.yaml.snakeyaml.tokens.StreamStartToken;
import org.yaml.snakeyaml.tokens.TagToken;
import org.yaml.snakeyaml.tokens.TagTuple;
import org.yaml.snakeyaml.tokens.Token;
import org.yaml.snakeyaml.util.ArrayStack;

public class ParserImpl implements Parser {

    private static final Map DEFAULT_TAGS = new HashMap();
    protected final Scanner scanner;
    private Event currentEvent;
    private final ArrayStack states;
    private final ArrayStack marks;
    private Production state;
    private VersionTagsTuple directives;

    public ParserImpl(StreamReader reader) {
        this((Scanner) (new ScannerImpl(reader)));
    }

    public ParserImpl(StreamReader reader, boolean emitComments) {
        this((Scanner) (new ScannerImpl(reader)).setEmitComments(emitComments));
    }

    public ParserImpl(Scanner scanner) {
        this.scanner = scanner;
        this.currentEvent = null;
        this.directives = new VersionTagsTuple((DumperOptions.Version) null, new HashMap(ParserImpl.DEFAULT_TAGS));
        this.states = new ArrayStack(100);
        this.marks = new ArrayStack(10);
        this.state = new ParserImpl.ParseStreamStart((ParserImpl.SyntheticClass_1) null);
    }

    public boolean checkEvent(Event.ID choice) {
        this.peekEvent();
        return this.currentEvent != null && this.currentEvent.is(choice);
    }

    public Event peekEvent() {
        if (this.currentEvent == null && this.state != null) {
            this.currentEvent = this.state.produce();
        }

        return this.currentEvent;
    }

    public Event getEvent() {
        this.peekEvent();
        Event value = this.currentEvent;

        this.currentEvent = null;
        return value;
    }

    private CommentEvent produceCommentEvent(CommentToken token) {
        Mark startMark = token.getStartMark();
        Mark endMark = token.getEndMark();
        String value = token.getValue();
        CommentType type = token.getCommentType();

        return new CommentEvent(type, value, startMark, endMark);
    }

    private VersionTagsTuple processDirectives() {
        DumperOptions.Version yamlVersion = null;
        HashMap tagHandles = new HashMap();

        while (this.scanner.checkToken(new Token.ID[] { Token.ID.Directive})) {
            DirectiveToken i$ = (DirectiveToken) this.scanner.getToken();
            List key;

            if (i$.getName().equals("YAML")) {
                if (yamlVersion != null) {
                    throw new ParserException((String) null, (Mark) null, "found duplicate YAML directive", i$.getStartMark());
                }

                key = i$.getValue();
                Integer handle = (Integer) key.get(0);

                if (handle.intValue() != 1) {
                    throw new ParserException((String) null, (Mark) null, "found incompatible YAML document (version 1.* is required)", i$.getStartMark());
                }

                Integer prefix = (Integer) key.get(1);

                switch (prefix.intValue()) {
                case 0:
                    yamlVersion = DumperOptions.Version.V1_0;
                    break;

                default:
                    yamlVersion = DumperOptions.Version.V1_1;
                }
            } else if (i$.getName().equals("TAG")) {
                key = i$.getValue();
                String handle1 = (String) key.get(0);
                String prefix1 = (String) key.get(1);

                if (tagHandles.containsKey(handle1)) {
                    throw new ParserException((String) null, (Mark) null, "duplicate tag handle " + handle1, i$.getStartMark());
                }

                tagHandles.put(handle1, prefix1);
            }
        }

        if (yamlVersion != null || !tagHandles.isEmpty()) {
            Iterator i$1 = ParserImpl.DEFAULT_TAGS.keySet().iterator();

            while (i$1.hasNext()) {
                String key1 = (String) i$1.next();

                if (!tagHandles.containsKey(key1)) {
                    tagHandles.put(key1, ParserImpl.DEFAULT_TAGS.get(key1));
                }
            }

            this.directives = new VersionTagsTuple(yamlVersion, tagHandles);
        }

        return this.directives;
    }

    private Event parseFlowNode() {
        return this.parseNode(false, false);
    }

    private Event parseBlockNodeOrIndentlessSequence() {
        return this.parseNode(true, true);
    }

    private Event parseNode(boolean block, boolean indentlessSequence) {
        Mark startMark = null;
        Mark endMark = null;
        Mark tagMark = null;
        Object event;

        if (this.scanner.checkToken(new Token.ID[] { Token.ID.Alias})) {
            AliasToken anchor = (AliasToken) this.scanner.getToken();

            event = new AliasEvent(anchor.getValue(), anchor.getStartMark(), anchor.getEndMark());
            this.state = (Production) this.states.pop();
        } else {
            String anchor1 = null;
            TagTuple tagTokenTag = null;

            if (this.scanner.checkToken(new Token.ID[] { Token.ID.Anchor})) {
                AnchorToken tag = (AnchorToken) this.scanner.getToken();

                startMark = tag.getStartMark();
                endMark = tag.getEndMark();
                anchor1 = tag.getValue();
                if (this.scanner.checkToken(new Token.ID[] { Token.ID.Tag})) {
                    TagToken implicit = (TagToken) this.scanner.getToken();

                    tagMark = implicit.getStartMark();
                    endMark = implicit.getEndMark();
                    tagTokenTag = implicit.getValue();
                }
            } else if (this.scanner.checkToken(new Token.ID[] { Token.ID.Tag})) {
                TagToken tag1 = (TagToken) this.scanner.getToken();

                startMark = tag1.getStartMark();
                tagMark = startMark;
                endMark = tag1.getEndMark();
                tagTokenTag = tag1.getValue();
                if (this.scanner.checkToken(new Token.ID[] { Token.ID.Anchor})) {
                    AnchorToken implicit1 = (AnchorToken) this.scanner.getToken();

                    endMark = implicit1.getEndMark();
                    anchor1 = implicit1.getValue();
                }
            }

            String tag2 = null;
            String node;

            if (tagTokenTag != null) {
                String implicit2 = tagTokenTag.getHandle();

                node = tagTokenTag.getSuffix();
                if (implicit2 != null) {
                    if (!this.directives.getTags().containsKey(implicit2)) {
                        throw new ParserException("while parsing a node", startMark, "found undefined tag handle " + implicit2, tagMark);
                    }

                    tag2 = (String) this.directives.getTags().get(implicit2) + node;
                } else {
                    tag2 = node;
                }
            }

            if (startMark == null) {
                startMark = this.scanner.peekToken().getStartMark();
                endMark = startMark;
            }

            event = null;
            boolean implicit3 = tag2 == null || tag2.equals("!");

            if (indentlessSequence && this.scanner.checkToken(new Token.ID[] { Token.ID.BlockEntry})) {
                endMark = this.scanner.peekToken().getEndMark();
                event = new SequenceStartEvent(anchor1, tag2, implicit3, startMark, endMark, DumperOptions.FlowStyle.BLOCK);
                this.state = new ParserImpl.ParseIndentlessSequenceEntry((ParserImpl.SyntheticClass_1) null);
            } else if (this.scanner.checkToken(new Token.ID[] { Token.ID.Scalar})) {
                ScalarToken node1 = (ScalarToken) this.scanner.getToken();

                endMark = node1.getEndMark();
                ImplicitTuple token;

                if ((!node1.getPlain() || tag2 != null) && !"!".equals(tag2)) {
                    if (tag2 == null) {
                        token = new ImplicitTuple(false, true);
                    } else {
                        token = new ImplicitTuple(false, false);
                    }
                } else {
                    token = new ImplicitTuple(true, false);
                }

                event = new ScalarEvent(anchor1, tag2, token, node1.getValue(), startMark, endMark, node1.getStyle());
                this.state = (Production) this.states.pop();
            } else if (this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                event = this.produceCommentEvent((CommentToken) this.scanner.getToken());
            } else if (this.scanner.checkToken(new Token.ID[] { Token.ID.FlowSequenceStart})) {
                endMark = this.scanner.peekToken().getEndMark();
                event = new SequenceStartEvent(anchor1, tag2, implicit3, startMark, endMark, DumperOptions.FlowStyle.FLOW);
                this.state = new ParserImpl.ParseFlowSequenceFirstEntry((ParserImpl.SyntheticClass_1) null);
            } else if (this.scanner.checkToken(new Token.ID[] { Token.ID.FlowMappingStart})) {
                endMark = this.scanner.peekToken().getEndMark();
                event = new MappingStartEvent(anchor1, tag2, implicit3, startMark, endMark, DumperOptions.FlowStyle.FLOW);
                this.state = new ParserImpl.ParseFlowMappingFirstKey((ParserImpl.SyntheticClass_1) null);
            } else if (block && this.scanner.checkToken(new Token.ID[] { Token.ID.BlockSequenceStart})) {
                endMark = this.scanner.peekToken().getStartMark();
                event = new SequenceStartEvent(anchor1, tag2, implicit3, startMark, endMark, DumperOptions.FlowStyle.BLOCK);
                this.state = new ParserImpl.ParseBlockSequenceFirstEntry((ParserImpl.SyntheticClass_1) null);
            } else if (block && this.scanner.checkToken(new Token.ID[] { Token.ID.BlockMappingStart})) {
                endMark = this.scanner.peekToken().getStartMark();
                event = new MappingStartEvent(anchor1, tag2, implicit3, startMark, endMark, DumperOptions.FlowStyle.BLOCK);
                this.state = new ParserImpl.ParseBlockMappingFirstKey((ParserImpl.SyntheticClass_1) null);
            } else {
                if (anchor1 == null && tag2 == null) {
                    if (block) {
                        node = "block";
                    } else {
                        node = "flow";
                    }

                    Token token1 = this.scanner.peekToken();

                    throw new ParserException("while parsing a " + node + " node", startMark, "expected the node content, but found \'" + token1.getTokenId() + "\'", token1.getStartMark());
                }

                event = new ScalarEvent(anchor1, tag2, new ImplicitTuple(implicit3, false), "", startMark, endMark, DumperOptions.ScalarStyle.PLAIN);
                this.state = (Production) this.states.pop();
            }
        }

        return (Event) event;
    }

    private Event processEmptyScalar(Mark mark) {
        return new ScalarEvent((String) null, (String) null, new ImplicitTuple(true, false), "", mark, mark, DumperOptions.ScalarStyle.PLAIN);
    }

    static {
        ParserImpl.DEFAULT_TAGS.put("!", "!");
        ParserImpl.DEFAULT_TAGS.put("!!", "tag:yaml.org,2002:");
    }

    static class SyntheticClass_1 {    }

    private class ParseFlowMappingEmptyValue implements Production {

        private ParseFlowMappingEmptyValue() {}

        public Event produce() {
            ParserImpl.this.state = ParserImpl.this.new ParseFlowMappingKey(false);
            return ParserImpl.this.processEmptyScalar(ParserImpl.this.scanner.peekToken().getStartMark());
        }

        ParseFlowMappingEmptyValue(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseFlowMappingValue implements Production {

        private ParseFlowMappingValue() {}

        public Event produce() {
            Token token;

            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Value})) {
                token = ParserImpl.this.scanner.getToken();
                if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.FlowEntry, Token.ID.FlowMappingEnd})) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseFlowMappingKey(false));
                    return ParserImpl.this.parseFlowNode();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseFlowMappingKey(false);
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseFlowMappingKey(false);
                token = ParserImpl.this.scanner.peekToken();
                return ParserImpl.this.processEmptyScalar(token.getStartMark());
            }
        }

        ParseFlowMappingValue(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseFlowMappingKey implements Production {

        private boolean first = false;

        public ParseFlowMappingKey(boolean first) {
            this.first = first;
        }

        public Event produce() {
            Token token;

            if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.FlowMappingEnd})) {
                if (!this.first) {
                    if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.FlowEntry})) {
                        token = ParserImpl.this.scanner.peekToken();
                        throw new ParserException("while parsing a flow mapping", (Mark) ParserImpl.this.marks.pop(), "expected \',\' or \'}\', but got " + token.getTokenId(), token.getStartMark());
                    }

                    ParserImpl.this.scanner.getToken();
                }

                if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Key})) {
                    token = ParserImpl.this.scanner.getToken();
                    if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Value, Token.ID.FlowEntry, Token.ID.FlowMappingEnd})) {
                        ParserImpl.this.states.push(ParserImpl.this.new ParseFlowMappingValue((ParserImpl.SyntheticClass_1) null));
                        return ParserImpl.this.parseFlowNode();
                    }

                    ParserImpl.this.state = ParserImpl.this.new ParseFlowMappingValue((ParserImpl.SyntheticClass_1) null);
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }

                if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.FlowMappingEnd})) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseFlowMappingEmptyValue((ParserImpl.SyntheticClass_1) null));
                    return ParserImpl.this.parseFlowNode();
                }
            }

            token = ParserImpl.this.scanner.getToken();
            MappingEndEvent event = new MappingEndEvent(token.getStartMark(), token.getEndMark());

            ParserImpl.this.marks.pop();
            if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                ParserImpl.this.state = (Production) ParserImpl.this.states.pop();
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseFlowEndComment((ParserImpl.SyntheticClass_1) null);
            }

            return event;
        }
    }

    private class ParseFlowMappingFirstKey implements Production {

        private ParseFlowMappingFirstKey() {}

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();

            ParserImpl.this.marks.push(token.getStartMark());
            return (ParserImpl.this.new ParseFlowMappingKey(true)).produce();
        }

        ParseFlowMappingFirstKey(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseFlowSequenceEntryMappingEnd implements Production {

        private ParseFlowSequenceEntryMappingEnd() {}

        public Event produce() {
            ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntry(false);
            Token token = ParserImpl.this.scanner.peekToken();

            return new MappingEndEvent(token.getStartMark(), token.getEndMark());
        }

        ParseFlowSequenceEntryMappingEnd(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseFlowSequenceEntryMappingValue implements Production {

        private ParseFlowSequenceEntryMappingValue() {}

        public Event produce() {
            Token token;

            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Value})) {
                token = ParserImpl.this.scanner.getToken();
                if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.FlowEntry, Token.ID.FlowSequenceEnd})) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseFlowSequenceEntryMappingEnd((ParserImpl.SyntheticClass_1) null));
                    return ParserImpl.this.parseFlowNode();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntryMappingEnd((ParserImpl.SyntheticClass_1) null);
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntryMappingEnd((ParserImpl.SyntheticClass_1) null);
                token = ParserImpl.this.scanner.peekToken();
                return ParserImpl.this.processEmptyScalar(token.getStartMark());
            }
        }

        ParseFlowSequenceEntryMappingValue(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseFlowSequenceEntryMappingKey implements Production {

        private ParseFlowSequenceEntryMappingKey() {}

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();

            if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Value, Token.ID.FlowEntry, Token.ID.FlowSequenceEnd})) {
                ParserImpl.this.states.push(ParserImpl.this.new ParseFlowSequenceEntryMappingValue((ParserImpl.SyntheticClass_1) null));
                return ParserImpl.this.parseFlowNode();
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntryMappingValue((ParserImpl.SyntheticClass_1) null);
                return ParserImpl.this.processEmptyScalar(token.getEndMark());
            }
        }

        ParseFlowSequenceEntryMappingKey(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseFlowEndComment implements Production {

        private ParseFlowEndComment() {}

        public Event produce() {
            CommentEvent event = ParserImpl.this.produceCommentEvent((CommentToken) ParserImpl.this.scanner.getToken());

            if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                ParserImpl.this.state = (Production) ParserImpl.this.states.pop();
            }

            return event;
        }

        ParseFlowEndComment(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseFlowSequenceEntry implements Production {

        private boolean first = false;

        public ParseFlowSequenceEntry(boolean first) {
            this.first = first;
        }

        public Event produce() {
            Token token;

            if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.FlowSequenceEnd})) {
                if (!this.first) {
                    if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.FlowEntry})) {
                        token = ParserImpl.this.scanner.peekToken();
                        throw new ParserException("while parsing a flow sequence", (Mark) ParserImpl.this.marks.pop(), "expected \',\' or \']\', but got " + token.getTokenId(), token.getStartMark());
                    }

                    ParserImpl.this.scanner.getToken();
                }

                if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Key})) {
                    token = ParserImpl.this.scanner.peekToken();
                    MappingStartEvent event1 = new MappingStartEvent((String) null, (String) null, true, token.getStartMark(), token.getEndMark(), DumperOptions.FlowStyle.FLOW);

                    ParserImpl.this.state = ParserImpl.this.new ParseFlowSequenceEntryMappingKey((ParserImpl.SyntheticClass_1) null);
                    return event1;
                }

                if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.FlowSequenceEnd})) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseFlowSequenceEntry(false));
                    return ParserImpl.this.parseFlowNode();
                }
            }

            token = ParserImpl.this.scanner.getToken();
            SequenceEndEvent event = new SequenceEndEvent(token.getStartMark(), token.getEndMark());

            ParserImpl.this.marks.pop();
            if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                ParserImpl.this.state = (Production) ParserImpl.this.states.pop();
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseFlowEndComment((ParserImpl.SyntheticClass_1) null);
            }

            return event;
        }
    }

    private class ParseFlowSequenceFirstEntry implements Production {

        private ParseFlowSequenceFirstEntry() {}

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();

            ParserImpl.this.marks.push(token.getStartMark());
            return (ParserImpl.this.new ParseFlowSequenceEntry(true)).produce();
        }

        ParseFlowSequenceFirstEntry(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseBlockMappingValueComment implements Production {

        private ParseBlockMappingValueComment() {}

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                return ParserImpl.this.parseBlockNodeOrIndentlessSequence();
            } else if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Key, Token.ID.Value, Token.ID.BlockEnd})) {
                ParserImpl.this.states.push(ParserImpl.this.new ParseBlockMappingKey((ParserImpl.SyntheticClass_1) null));
                return ParserImpl.this.parseBlockNodeOrIndentlessSequence();
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseBlockMappingKey((ParserImpl.SyntheticClass_1) null);
                Token token = ParserImpl.this.scanner.getToken();

                return ParserImpl.this.processEmptyScalar(token.getEndMark());
            }
        }

        ParseBlockMappingValueComment(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseBlockMappingValue implements Production {

        private ParseBlockMappingValue() {}

        public Event produce() {
            Token token;

            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Value})) {
                token = ParserImpl.this.scanner.getToken();
                if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                    ParserImpl.this.state = ParserImpl.this.new ParseBlockMappingValueComment((ParserImpl.SyntheticClass_1) null);
                    return ParserImpl.this.state.produce();
                } else if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Key, Token.ID.Value, Token.ID.BlockEnd})) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseBlockMappingKey((ParserImpl.SyntheticClass_1) null));
                    return ParserImpl.this.parseBlockNodeOrIndentlessSequence();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseBlockMappingKey((ParserImpl.SyntheticClass_1) null);
                    return ParserImpl.this.processEmptyScalar(token.getEndMark());
                }
            } else if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Scalar})) {
                ParserImpl.this.states.push(ParserImpl.this.new ParseBlockMappingKey((ParserImpl.SyntheticClass_1) null));
                return ParserImpl.this.parseBlockNodeOrIndentlessSequence();
            } else {
                ParserImpl.this.state = ParserImpl.this.new ParseBlockMappingKey((ParserImpl.SyntheticClass_1) null);
                token = ParserImpl.this.scanner.peekToken();
                return ParserImpl.this.processEmptyScalar(token.getStartMark());
            }
        }

        ParseBlockMappingValue(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseBlockMappingKey implements Production {

        private ParseBlockMappingKey() {}

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                return ParserImpl.this.produceCommentEvent((CommentToken) ParserImpl.this.scanner.getToken());
            } else {
                Token token;

                if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Key})) {
                    token = ParserImpl.this.scanner.getToken();
                    if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Key, Token.ID.Value, Token.ID.BlockEnd})) {
                        ParserImpl.this.states.push(ParserImpl.this.new ParseBlockMappingValue((ParserImpl.SyntheticClass_1) null));
                        return ParserImpl.this.parseBlockNodeOrIndentlessSequence();
                    } else {
                        ParserImpl.this.state = ParserImpl.this.new ParseBlockMappingValue((ParserImpl.SyntheticClass_1) null);
                        return ParserImpl.this.processEmptyScalar(token.getEndMark());
                    }
                } else if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.BlockEnd})) {
                    token = ParserImpl.this.scanner.peekToken();
                    throw new ParserException("while parsing a block mapping", (Mark) ParserImpl.this.marks.pop(), "expected <block end>, but found \'" + token.getTokenId() + "\'", token.getStartMark());
                } else {
                    token = ParserImpl.this.scanner.getToken();
                    MappingEndEvent event = new MappingEndEvent(token.getStartMark(), token.getEndMark());

                    ParserImpl.this.state = (Production) ParserImpl.this.states.pop();
                    ParserImpl.this.marks.pop();
                    return event;
                }
            }
        }

        ParseBlockMappingKey(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseBlockMappingFirstKey implements Production {

        private ParseBlockMappingFirstKey() {}

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();

            ParserImpl.this.marks.push(token.getStartMark());
            return (ParserImpl.this.new ParseBlockMappingKey((ParserImpl.SyntheticClass_1) null)).produce();
        }

        ParseBlockMappingFirstKey(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseIndentlessSequenceEntry implements Production {

        private ParseIndentlessSequenceEntry() {}

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                return ParserImpl.this.produceCommentEvent((CommentToken) ParserImpl.this.scanner.getToken());
            } else {
                Token token;

                if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.BlockEntry})) {
                    token = ParserImpl.this.scanner.getToken();
                    if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.BlockEntry, Token.ID.Key, Token.ID.Value, Token.ID.BlockEnd})) {
                        ParserImpl.this.states.push(ParserImpl.this.new ParseIndentlessSequenceEntry());
                        return (ParserImpl.this.new ParseBlockNode((ParserImpl.SyntheticClass_1) null)).produce();
                    } else {
                        ParserImpl.this.state = ParserImpl.this.new ParseIndentlessSequenceEntry();
                        return ParserImpl.this.processEmptyScalar(token.getEndMark());
                    }
                } else {
                    token = ParserImpl.this.scanner.peekToken();
                    SequenceEndEvent event = new SequenceEndEvent(token.getStartMark(), token.getEndMark());

                    ParserImpl.this.state = (Production) ParserImpl.this.states.pop();
                    return event;
                }
            }
        }

        ParseIndentlessSequenceEntry(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseBlockSequenceEntry implements Production {

        private ParseBlockSequenceEntry() {}

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                return ParserImpl.this.produceCommentEvent((CommentToken) ParserImpl.this.scanner.getToken());
            } else if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.BlockEntry})) {
                BlockEntryToken token1 = (BlockEntryToken) ParserImpl.this.scanner.getToken();

                if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.BlockEntry, Token.ID.BlockEnd})) {
                    ParserImpl.this.states.push(ParserImpl.this.new ParseBlockSequenceEntry());
                    return (ParserImpl.this.new ParseBlockNode((ParserImpl.SyntheticClass_1) null)).produce();
                } else {
                    ParserImpl.this.state = ParserImpl.this.new ParseBlockSequenceEntry();
                    return ParserImpl.this.processEmptyScalar(token1.getEndMark());
                }
            } else {
                Token token;

                if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.BlockEnd})) {
                    token = ParserImpl.this.scanner.peekToken();
                    throw new ParserException("while parsing a block collection", (Mark) ParserImpl.this.marks.pop(), "expected <block end>, but found \'" + token.getTokenId() + "\'", token.getStartMark());
                } else {
                    token = ParserImpl.this.scanner.getToken();
                    SequenceEndEvent event = new SequenceEndEvent(token.getStartMark(), token.getEndMark());

                    ParserImpl.this.state = (Production) ParserImpl.this.states.pop();
                    ParserImpl.this.marks.pop();
                    return event;
                }
            }
        }

        ParseBlockSequenceEntry(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseBlockSequenceFirstEntry implements Production {

        private ParseBlockSequenceFirstEntry() {}

        public Event produce() {
            Token token = ParserImpl.this.scanner.getToken();

            ParserImpl.this.marks.push(token.getStartMark());
            return (ParserImpl.this.new ParseBlockSequenceEntry((ParserImpl.SyntheticClass_1) null)).produce();
        }

        ParseBlockSequenceFirstEntry(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseBlockNode implements Production {

        private ParseBlockNode() {}

        public Event produce() {
            return ParserImpl.this.parseNode(true, false);
        }

        ParseBlockNode(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseDocumentContent implements Production {

        private ParseDocumentContent() {}

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                return ParserImpl.this.produceCommentEvent((CommentToken) ParserImpl.this.scanner.getToken());
            } else if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Directive, Token.ID.DocumentStart, Token.ID.DocumentEnd, Token.ID.StreamEnd})) {
                Event event = ParserImpl.this.processEmptyScalar(ParserImpl.this.scanner.peekToken().getStartMark());

                ParserImpl.this.state = (Production) ParserImpl.this.states.pop();
                return event;
            } else {
                ParserImpl.ParseBlockNode p = ParserImpl.this.new ParseBlockNode((ParserImpl.SyntheticClass_1) null);

                return p.produce();
            }
        }

        ParseDocumentContent(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseDocumentEnd implements Production {

        private ParseDocumentEnd() {}

        public Event produce() {
            Token token = ParserImpl.this.scanner.peekToken();
            Mark startMark = token.getStartMark();
            Mark endMark = startMark;
            boolean explicit = false;

            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.DocumentEnd})) {
                token = ParserImpl.this.scanner.getToken();
                endMark = token.getEndMark();
                explicit = true;
            }

            DocumentEndEvent event = new DocumentEndEvent(startMark, endMark, explicit);

            ParserImpl.this.state = ParserImpl.this.new ParseDocumentStart((ParserImpl.SyntheticClass_1) null);
            return event;
        }

        ParseDocumentEnd(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseDocumentStart implements Production {

        private ParseDocumentStart() {}

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                return ParserImpl.this.produceCommentEvent((CommentToken) ParserImpl.this.scanner.getToken());
            } else {
                while (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.DocumentEnd})) {
                    ParserImpl.this.scanner.getToken();
                }

                if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                    return ParserImpl.this.produceCommentEvent((CommentToken) ParserImpl.this.scanner.getToken());
                } else {
                    if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.StreamEnd})) {
                        Token token = ParserImpl.this.scanner.peekToken();
                        Mark startMark = token.getStartMark();
                        VersionTagsTuple tuple = ParserImpl.this.processDirectives();

                        while (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                            ParserImpl.this.scanner.getToken();
                        }

                        if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.StreamEnd})) {
                            if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.DocumentStart})) {
                                throw new ParserException((String) null, (Mark) null, "expected \'<document start>\', but found \'" + ParserImpl.this.scanner.peekToken().getTokenId() + "\'", ParserImpl.this.scanner.peekToken().getStartMark());
                            }

                            token = ParserImpl.this.scanner.getToken();
                            Mark endMark = token.getEndMark();
                            DocumentStartEvent event1 = new DocumentStartEvent(startMark, endMark, true, tuple.getVersion(), tuple.getTags());

                            ParserImpl.this.states.push(ParserImpl.this.new ParseDocumentEnd((ParserImpl.SyntheticClass_1) null));
                            ParserImpl.this.state = ParserImpl.this.new ParseDocumentContent((ParserImpl.SyntheticClass_1) null);
                            return event1;
                        }
                    }

                    StreamEndToken token1 = (StreamEndToken) ParserImpl.this.scanner.getToken();
                    StreamEndEvent event = new StreamEndEvent(token1.getStartMark(), token1.getEndMark());

                    if (!ParserImpl.this.states.isEmpty()) {
                        throw new YAMLException("Unexpected end of stream. States left: " + ParserImpl.this.states);
                    } else if (!ParserImpl.this.marks.isEmpty()) {
                        throw new YAMLException("Unexpected end of stream. Marks left: " + ParserImpl.this.marks);
                    } else {
                        ParserImpl.this.state = null;
                        return event;
                    }
                }
            }
        }

        ParseDocumentStart(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseImplicitDocumentStart implements Production {

        private ParseImplicitDocumentStart() {}

        public Event produce() {
            if (ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Comment})) {
                return ParserImpl.this.produceCommentEvent((CommentToken) ParserImpl.this.scanner.getToken());
            } else if (!ParserImpl.this.scanner.checkToken(new Token.ID[] { Token.ID.Directive, Token.ID.DocumentStart, Token.ID.StreamEnd})) {
                ParserImpl.this.directives = new VersionTagsTuple((DumperOptions.Version) null, ParserImpl.DEFAULT_TAGS);
                Token p1 = ParserImpl.this.scanner.peekToken();
                Mark startMark = p1.getStartMark();
                DocumentStartEvent event = new DocumentStartEvent(startMark, startMark, false, (DumperOptions.Version) null, (Map) null);

                ParserImpl.this.states.push(ParserImpl.this.new ParseDocumentEnd((ParserImpl.SyntheticClass_1) null));
                ParserImpl.this.state = ParserImpl.this.new ParseBlockNode((ParserImpl.SyntheticClass_1) null);
                return event;
            } else {
                ParserImpl.ParseDocumentStart p = ParserImpl.this.new ParseDocumentStart((ParserImpl.SyntheticClass_1) null);

                return p.produce();
            }
        }

        ParseImplicitDocumentStart(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }

    private class ParseStreamStart implements Production {

        private ParseStreamStart() {}

        public Event produce() {
            StreamStartToken token = (StreamStartToken) ParserImpl.this.scanner.getToken();
            StreamStartEvent event = new StreamStartEvent(token.getStartMark(), token.getEndMark());

            ParserImpl.this.state = ParserImpl.this.new ParseImplicitDocumentStart((ParserImpl.SyntheticClass_1) null);
            return event;
        }

        ParseStreamStart(ParserImpl.SyntheticClass_1 x1) {
            this();
        }
    }
}
