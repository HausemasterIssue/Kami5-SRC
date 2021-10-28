package org.yaml.snakeyaml.constructor;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

public class SafeConstructor extends BaseConstructor {

    public static final SafeConstructor.ConstructUndefined undefinedConstructor = new SafeConstructor.ConstructUndefined();
    private static final Map BOOL_VALUES = new HashMap();
    private static final int[][] RADIX_MAX;
    private static final Pattern TIMESTAMP_REGEXP;
    private static final Pattern YMD_REGEXP;

    public SafeConstructor() {
        this(new LoaderOptions());
    }

    public SafeConstructor(LoaderOptions loadingConfig) {
        super(loadingConfig);
        this.yamlConstructors.put(Tag.NULL, new SafeConstructor.ConstructYamlNull());
        this.yamlConstructors.put(Tag.BOOL, new SafeConstructor.ConstructYamlBool());
        this.yamlConstructors.put(Tag.INT, new SafeConstructor.ConstructYamlInt());
        this.yamlConstructors.put(Tag.FLOAT, new SafeConstructor.ConstructYamlFloat());
        this.yamlConstructors.put(Tag.BINARY, new SafeConstructor.ConstructYamlBinary());
        this.yamlConstructors.put(Tag.TIMESTAMP, new SafeConstructor.ConstructYamlTimestamp());
        this.yamlConstructors.put(Tag.OMAP, new SafeConstructor.ConstructYamlOmap());
        this.yamlConstructors.put(Tag.PAIRS, new SafeConstructor.ConstructYamlPairs());
        this.yamlConstructors.put(Tag.SET, new SafeConstructor.ConstructYamlSet());
        this.yamlConstructors.put(Tag.STR, new SafeConstructor.ConstructYamlStr());
        this.yamlConstructors.put(Tag.SEQ, new SafeConstructor.ConstructYamlSeq());
        this.yamlConstructors.put(Tag.MAP, new SafeConstructor.ConstructYamlMap());
        this.yamlConstructors.put((Object) null, SafeConstructor.undefinedConstructor);
        this.yamlClassConstructors.put(NodeId.scalar, SafeConstructor.undefinedConstructor);
        this.yamlClassConstructors.put(NodeId.sequence, SafeConstructor.undefinedConstructor);
        this.yamlClassConstructors.put(NodeId.mapping, SafeConstructor.undefinedConstructor);
    }

    protected void flattenMapping(MappingNode node) {
        this.processDuplicateKeys(node);
        if (node.isMerged()) {
            node.setValue(this.mergeNode(node, true, new HashMap(), new ArrayList()));
        }

    }

    protected void processDuplicateKeys(MappingNode node) {
        List nodeValue = node.getValue();
        HashMap keys = new HashMap(nodeValue.size());
        TreeSet toRemove = new TreeSet();
        int i = 0;

        Iterator indices2remove;

        for (indices2remove = nodeValue.iterator(); indices2remove.hasNext(); ++i) {
            NodeTuple tuple = (NodeTuple) indices2remove.next();
            Node keyNode = tuple.getKeyNode();

            if (!keyNode.getTag().equals(Tag.MERGE)) {
                Object key = this.constructObject(keyNode);

                if (key != null) {
                    try {
                        key.hashCode();
                    } catch (Exception exception) {
                        throw new ConstructorException("while constructing a mapping", node.getStartMark(), "found unacceptable key " + key, tuple.getKeyNode().getStartMark(), exception);
                    }
                }

                Integer prevIndex = (Integer) keys.put(key, Integer.valueOf(i));

                if (prevIndex != null) {
                    if (!this.isAllowDuplicateKeys()) {
                        throw new DuplicateKeyException(node.getStartMark(), key, tuple.getKeyNode().getStartMark());
                    }

                    toRemove.add(prevIndex);
                }
            }
        }

        indices2remove = toRemove.descendingIterator();

        while (indices2remove.hasNext()) {
            nodeValue.remove(((Integer) indices2remove.next()).intValue());
        }

    }

    private List mergeNode(MappingNode node, boolean isPreffered, Map key2index, List values) {
        Iterator iter = node.getValue().iterator();

        while (true) {
            label32:
            while (iter.hasNext()) {
                NodeTuple nodeTuple = (NodeTuple) iter.next();
                Node keyNode = nodeTuple.getKeyNode();
                Node valueNode = nodeTuple.getValueNode();

                if (keyNode.getTag().equals(Tag.MERGE)) {
                    iter.remove();
                    switch (valueNode.getNodeId()) {
                    case mapping:
                        MappingNode key1 = (MappingNode) valueNode;

                        this.mergeNode(key1, false, key2index, values);
                        break;

                    case sequence:
                        SequenceNode sn = (SequenceNode) valueNode;
                        List vals = sn.getValue();
                        Iterator i$ = vals.iterator();

                        while (true) {
                            if (!i$.hasNext()) {
                                continue label32;
                            }

                            Node subnode = (Node) i$.next();

                            if (!(subnode instanceof MappingNode)) {
                                throw new ConstructorException("while constructing a mapping", node.getStartMark(), "expected a mapping for merging, but found " + subnode.getNodeId(), subnode.getStartMark());
                            }

                            MappingNode mnode = (MappingNode) subnode;

                            this.mergeNode(mnode, false, key2index, values);
                        }

                    default:
                        throw new ConstructorException("while constructing a mapping", node.getStartMark(), "expected a mapping or list of mappings for merging, but found " + valueNode.getNodeId(), valueNode.getStartMark());
                    }
                } else {
                    Object key = this.constructObject(keyNode);

                    if (!key2index.containsKey(key)) {
                        values.add(nodeTuple);
                        key2index.put(key, Integer.valueOf(values.size() - 1));
                    } else if (isPreffered) {
                        values.set(((Integer) key2index.get(key)).intValue(), nodeTuple);
                    }
                }
            }

            return values;
        }
    }

    protected void constructMapping2ndStep(MappingNode node, Map mapping) {
        this.flattenMapping(node);
        super.constructMapping2ndStep(node, mapping);
    }

    protected void constructSet2ndStep(MappingNode node, Set set) {
        this.flattenMapping(node);
        super.constructSet2ndStep(node, set);
    }

    private static int maxLen(int max, int radix) {
        return Integer.toString(max, radix).length();
    }

    private static int maxLen(long max, int radix) {
        return Long.toString(max, radix).length();
    }

    private Number createNumber(int sign, String number, int radix) {
        int len = number != null ? number.length() : 0;

        if (sign < 0) {
            number = "-" + number;
        }

        int[] maxArr = radix < SafeConstructor.RADIX_MAX.length ? SafeConstructor.RADIX_MAX[radix] : null;

        if (maxArr != null) {
            boolean result = len > maxArr[0];

            if (result) {
                if (len > maxArr[1]) {
                    return new BigInteger(number, radix);
                }

                return createLongOrBigInteger(number, radix);
            }
        }

        Object result1;

        try {
            result1 = Integer.valueOf(number, radix);
        } catch (NumberFormatException numberformatexception) {
            result1 = createLongOrBigInteger(number, radix);
        }

        return (Number) result1;
    }

    protected static Number createLongOrBigInteger(String number, int radix) {
        try {
            return Long.valueOf(number, radix);
        } catch (NumberFormatException numberformatexception) {
            return new BigInteger(number, radix);
        }
    }

    static {
        SafeConstructor.BOOL_VALUES.put("yes", Boolean.TRUE);
        SafeConstructor.BOOL_VALUES.put("no", Boolean.FALSE);
        SafeConstructor.BOOL_VALUES.put("true", Boolean.TRUE);
        SafeConstructor.BOOL_VALUES.put("false", Boolean.FALSE);
        SafeConstructor.BOOL_VALUES.put("on", Boolean.TRUE);
        SafeConstructor.BOOL_VALUES.put("off", Boolean.FALSE);
        RADIX_MAX = new int[17][2];
        int[] radixList = new int[] { 2, 8, 10, 16};
        int[] arr$ = radixList;
        int len$ = radixList.length;

        for (int i$ = 0; i$ < len$; ++i$) {
            int radix = arr$[i$];

            SafeConstructor.RADIX_MAX[radix] = new int[] { maxLen(Integer.MAX_VALUE, radix), maxLen(Long.MAX_VALUE, radix)};
        }

        TIMESTAMP_REGEXP = Pattern.compile("^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)(?:(?:[Tt]|[ \t]+)([0-9][0-9]?):([0-9][0-9]):([0-9][0-9])(?:\\.([0-9]*))?(?:[ \t]*(?:Z|([-+][0-9][0-9]?)(?::([0-9][0-9])?)?))?)?$");
        YMD_REGEXP = Pattern.compile("^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)$");
    }

    static class SyntheticClass_1 {

    }

    public static final class ConstructUndefined extends AbstractConstruct {

        public Object construct(Node node) {
            throw new ConstructorException((String) null, (Mark) null, "could not determine a constructor for the tag " + node.getTag(), node.getStartMark());
        }
    }

    public class ConstructYamlMap implements Construct {

        public Object construct(Node node) {
            MappingNode mnode = (MappingNode) node;

            return node.isTwoStepsConstruction() ? SafeConstructor.this.createDefaultMap(mnode.getValue().size()) : SafeConstructor.this.constructMapping(mnode);
        }

        public void construct2ndStep(Node node, Object object) {
            if (node.isTwoStepsConstruction()) {
                SafeConstructor.this.constructMapping2ndStep((MappingNode) node, (Map) object);
            } else {
                throw new YAMLException("Unexpected recursive mapping structure. Node: " + node);
            }
        }
    }

    public class ConstructYamlSeq implements Construct {

        public Object construct(Node node) {
            SequenceNode seqNode = (SequenceNode) node;

            return node.isTwoStepsConstruction() ? SafeConstructor.this.newList(seqNode) : SafeConstructor.this.constructSequence(seqNode);
        }

        public void construct2ndStep(Node node, Object data) {
            if (node.isTwoStepsConstruction()) {
                SafeConstructor.this.constructSequenceStep2((SequenceNode) node, (List) data);
            } else {
                throw new YAMLException("Unexpected recursive sequence structure. Node: " + node);
            }
        }
    }

    public class ConstructYamlStr extends AbstractConstruct {

        public Object construct(Node node) {
            return SafeConstructor.this.constructScalar((ScalarNode) node);
        }
    }

    public class ConstructYamlSet implements Construct {

        public Object construct(Node node) {
            return node.isTwoStepsConstruction() ? (SafeConstructor.this.constructedObjects.containsKey(node) ? SafeConstructor.this.constructedObjects.get(node) : SafeConstructor.this.createDefaultSet(((MappingNode) node).getValue().size())) : SafeConstructor.this.constructSet((MappingNode) node);
        }

        public void construct2ndStep(Node node, Object object) {
            if (node.isTwoStepsConstruction()) {
                SafeConstructor.this.constructSet2ndStep((MappingNode) node, (Set) object);
            } else {
                throw new YAMLException("Unexpected recursive set structure. Node: " + node);
            }
        }
    }

    public class ConstructYamlPairs extends AbstractConstruct {

        public Object construct(Node node) {
            if (!(node instanceof SequenceNode)) {
                throw new ConstructorException("while constructing pairs", node.getStartMark(), "expected a sequence, but found " + node.getNodeId(), node.getStartMark());
            } else {
                SequenceNode snode = (SequenceNode) node;
                ArrayList pairs = new ArrayList(snode.getValue().size());
                Iterator i$ = snode.getValue().iterator();

                while (i$.hasNext()) {
                    Node subnode = (Node) i$.next();

                    if (!(subnode instanceof MappingNode)) {
                        throw new ConstructorException("while constructingpairs", node.getStartMark(), "expected a mapping of length 1, but found " + subnode.getNodeId(), subnode.getStartMark());
                    }

                    MappingNode mnode = (MappingNode) subnode;

                    if (mnode.getValue().size() != 1) {
                        throw new ConstructorException("while constructing pairs", node.getStartMark(), "expected a single mapping item, but found " + mnode.getValue().size() + " items", mnode.getStartMark());
                    }

                    Node keyNode = ((NodeTuple) mnode.getValue().get(0)).getKeyNode();
                    Node valueNode = ((NodeTuple) mnode.getValue().get(0)).getValueNode();
                    Object key = SafeConstructor.this.constructObject(keyNode);
                    Object value = SafeConstructor.this.constructObject(valueNode);

                    pairs.add(new Object[] { key, value});
                }

                return pairs;
            }
        }
    }

    public class ConstructYamlOmap extends AbstractConstruct {

        public Object construct(Node node) {
            LinkedHashMap omap = new LinkedHashMap();

            if (!(node instanceof SequenceNode)) {
                throw new ConstructorException("while constructing an ordered map", node.getStartMark(), "expected a sequence, but found " + node.getNodeId(), node.getStartMark());
            } else {
                SequenceNode snode = (SequenceNode) node;
                Iterator i$ = snode.getValue().iterator();

                while (i$.hasNext()) {
                    Node subnode = (Node) i$.next();

                    if (!(subnode instanceof MappingNode)) {
                        throw new ConstructorException("while constructing an ordered map", node.getStartMark(), "expected a mapping of length 1, but found " + subnode.getNodeId(), subnode.getStartMark());
                    }

                    MappingNode mnode = (MappingNode) subnode;

                    if (mnode.getValue().size() != 1) {
                        throw new ConstructorException("while constructing an ordered map", node.getStartMark(), "expected a single mapping item, but found " + mnode.getValue().size() + " items", mnode.getStartMark());
                    }

                    Node keyNode = ((NodeTuple) mnode.getValue().get(0)).getKeyNode();
                    Node valueNode = ((NodeTuple) mnode.getValue().get(0)).getValueNode();
                    Object key = SafeConstructor.this.constructObject(keyNode);
                    Object value = SafeConstructor.this.constructObject(valueNode);

                    omap.put(key, value);
                }

                return omap;
            }
        }
    }

    public static class ConstructYamlTimestamp extends AbstractConstruct {

        private Calendar calendar;

        public Calendar getCalendar() {
            return this.calendar;
        }

        public Object construct(Node node) {
            ScalarNode scalar = (ScalarNode) node;
            String nodeValue = scalar.getValue();
            Matcher match = SafeConstructor.YMD_REGEXP.matcher(nodeValue);
            String year_s;
            String month_s;
            String day_s;

            if (match.matches()) {
                year_s = match.group(1);
                month_s = match.group(2);
                day_s = match.group(3);
                this.calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                this.calendar.clear();
                this.calendar.set(1, Integer.parseInt(year_s));
                this.calendar.set(2, Integer.parseInt(month_s) - 1);
                this.calendar.set(5, Integer.parseInt(day_s));
                return this.calendar.getTime();
            } else {
                match = SafeConstructor.TIMESTAMP_REGEXP.matcher(nodeValue);
                if (!match.matches()) {
                    throw new YAMLException("Unexpected timestamp: " + nodeValue);
                } else {
                    year_s = match.group(1);
                    month_s = match.group(2);
                    day_s = match.group(3);
                    String hour_s = match.group(4);
                    String min_s = match.group(5);
                    String seconds = match.group(6);
                    String millis = match.group(7);

                    if (millis != null) {
                        seconds = seconds + "." + millis;
                    }

                    double fractions = Double.parseDouble(seconds);
                    int sec_s = (int) Math.round(Math.floor(fractions));
                    int usec = (int) Math.round((fractions - (double) sec_s) * 1000.0D);
                    String timezoneh_s = match.group(8);
                    String timezonem_s = match.group(9);
                    TimeZone timeZone;

                    if (timezoneh_s != null) {
                        String time = timezonem_s != null ? ":" + timezonem_s : "00";

                        timeZone = TimeZone.getTimeZone("GMT" + timezoneh_s + time);
                    } else {
                        timeZone = TimeZone.getTimeZone("UTC");
                    }

                    this.calendar = Calendar.getInstance(timeZone);
                    this.calendar.set(1, Integer.parseInt(year_s));
                    this.calendar.set(2, Integer.parseInt(month_s) - 1);
                    this.calendar.set(5, Integer.parseInt(day_s));
                    this.calendar.set(11, Integer.parseInt(hour_s));
                    this.calendar.set(12, Integer.parseInt(min_s));
                    this.calendar.set(13, sec_s);
                    this.calendar.set(14, usec);
                    return this.calendar.getTime();
                }
            }
        }
    }

    public class ConstructYamlBinary extends AbstractConstruct {

        public Object construct(Node node) {
            String noWhiteSpaces = SafeConstructor.this.constructScalar((ScalarNode) node).toString().replaceAll("\\s", "");
            byte[] decoded = Base64Coder.decode(noWhiteSpaces.toCharArray());

            return decoded;
        }
    }

    public class ConstructYamlFloat extends AbstractConstruct {

        public Object construct(Node node) {
            String value = SafeConstructor.this.constructScalar((ScalarNode) node).toString().replaceAll("_", "");
            byte sign = 1;
            char first = value.charAt(0);

            if (first == 45) {
                sign = -1;
                value = value.substring(1);
            } else if (first == 43) {
                value = value.substring(1);
            }

            String valLower = value.toLowerCase();

            if (".inf".equals(valLower)) {
                return Double.valueOf(sign == -1 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
            } else if (".nan".equals(valLower)) {
                return Double.valueOf(Double.NaN);
            } else if (value.indexOf(58) == -1) {
                Double odouble = Double.valueOf(value);

                return Double.valueOf(odouble.doubleValue() * (double) sign);
            } else {
                String[] d = value.split(":");
                int bes = 1;
                double val = 0.0D;
                int i = 0;

                for (int j = d.length; i < j; ++i) {
                    val += Double.parseDouble(d[j - i - 1]) * (double) bes;
                    bes *= 60;
                }

                return Double.valueOf((double) sign * val);
            }
        }
    }

    public class ConstructYamlInt extends AbstractConstruct {

        public Object construct(Node node) {
            String value = SafeConstructor.this.constructScalar((ScalarNode) node).toString().replaceAll("_", "");
            byte sign = 1;
            char first = value.charAt(0);

            if (first == 45) {
                sign = -1;
                value = value.substring(1);
            } else if (first == 43) {
                value = value.substring(1);
            }

            boolean base = true;

            if ("0".equals(value)) {
                return Integer.valueOf(0);
            } else {
                byte b0;

                if (value.startsWith("0b")) {
                    value = value.substring(2);
                    b0 = 2;
                } else if (value.startsWith("0x")) {
                    value = value.substring(2);
                    b0 = 16;
                } else {
                    if (!value.startsWith("0")) {
                        if (value.indexOf(58) == -1) {
                            return SafeConstructor.this.createNumber(sign, value, 10);
                        }

                        String[] digits = value.split(":");
                        int bes = 1;
                        int val = 0;
                        int i = 0;

                        for (int j = digits.length; i < j; ++i) {
                            val = (int) ((long) val + Long.parseLong(digits[j - i - 1]) * (long) bes);
                            bes *= 60;
                        }

                        return SafeConstructor.this.createNumber(sign, String.valueOf(val), 10);
                    }

                    value = value.substring(1);
                    b0 = 8;
                }

                return SafeConstructor.this.createNumber(sign, value, b0);
            }
        }
    }

    public class ConstructYamlBool extends AbstractConstruct {

        public Object construct(Node node) {
            String val = SafeConstructor.this.constructScalar((ScalarNode) node);

            return SafeConstructor.BOOL_VALUES.get(val.toLowerCase());
        }
    }

    public class ConstructYamlNull extends AbstractConstruct {

        public Object construct(Node node) {
            if (node != null) {
                SafeConstructor.this.constructScalar((ScalarNode) node);
            }

            return null;
        }
    }
}
