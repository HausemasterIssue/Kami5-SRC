package org.yaml.snakeyaml.representer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

public class Representer extends SafeRepresenter {

    protected Map typeDefinitions = Collections.emptyMap();

    public Representer() {
        this.representers.put((Object) null, new Representer.RepresentJavaBean());
    }

    public Representer(DumperOptions options) {
        super(options);
        this.representers.put((Object) null, new Representer.RepresentJavaBean());
    }

    public TypeDescription addTypeDescription(TypeDescription td) {
        if (Collections.EMPTY_MAP == this.typeDefinitions) {
            this.typeDefinitions = new HashMap();
        }

        if (td.getTag() != null) {
            this.addClassTag(td.getType(), td.getTag());
        }

        td.setPropertyUtils(this.getPropertyUtils());
        return (TypeDescription) this.typeDefinitions.put(td.getType(), td);
    }

    public void setPropertyUtils(PropertyUtils propertyUtils) {
        super.setPropertyUtils(propertyUtils);
        Collection tds = this.typeDefinitions.values();
        Iterator i$ = tds.iterator();

        while (i$.hasNext()) {
            TypeDescription typeDescription = (TypeDescription) i$.next();

            typeDescription.setPropertyUtils(propertyUtils);
        }

    }

    protected MappingNode representJavaBean(Set properties, Object javaBean) {
        ArrayList value = new ArrayList(properties.size());
        Tag customTag = (Tag) this.classTags.get(javaBean.getClass());
        Tag tag = customTag != null ? customTag : new Tag(javaBean.getClass());
        MappingNode node = new MappingNode(tag, value, DumperOptions.FlowStyle.AUTO);

        this.representedObjects.put(javaBean, node);
        DumperOptions.FlowStyle bestStyle = DumperOptions.FlowStyle.FLOW;
        Iterator i$ = properties.iterator();

        while (i$.hasNext()) {
            Property property = (Property) i$.next();
            Object memberValue = property.get(javaBean);
            Tag customPropertyTag = memberValue == null ? null : (Tag) this.classTags.get(memberValue.getClass());
            NodeTuple tuple = this.representJavaBeanProperty(javaBean, property, memberValue, customPropertyTag);

            if (tuple != null) {
                if (!((ScalarNode) tuple.getKeyNode()).isPlain()) {
                    bestStyle = DumperOptions.FlowStyle.BLOCK;
                }

                Node nodeValue = tuple.getValueNode();

                if (!(nodeValue instanceof ScalarNode) || !((ScalarNode) nodeValue).isPlain()) {
                    bestStyle = DumperOptions.FlowStyle.BLOCK;
                }

                value.add(tuple);
            }
        }

        if (this.defaultFlowStyle != DumperOptions.FlowStyle.AUTO) {
            node.setFlowStyle(this.defaultFlowStyle);
        } else {
            node.setFlowStyle(bestStyle);
        }

        return node;
    }

    protected NodeTuple representJavaBeanProperty(Object javaBean, Property property, Object propertyValue, Tag customTag) {
        ScalarNode nodeKey = (ScalarNode) this.representData(property.getName());
        boolean hasAlias = this.representedObjects.containsKey(propertyValue);
        Node nodeValue = this.representData(propertyValue);

        if (propertyValue != null && !hasAlias) {
            NodeId nodeId = nodeValue.getNodeId();

            if (customTag == null) {
                if (nodeId == NodeId.scalar) {
                    if (property.getType() != Enum.class && propertyValue instanceof Enum) {
                        nodeValue.setTag(Tag.STR);
                    }
                } else {
                    if (nodeId == NodeId.mapping && property.getType() == propertyValue.getClass() && !(propertyValue instanceof Map) && !nodeValue.getTag().equals(Tag.SET)) {
                        nodeValue.setTag(Tag.MAP);
                    }

                    this.checkGlobalTag(property, nodeValue, propertyValue);
                }
            }
        }

        return new NodeTuple(nodeKey, nodeValue);
    }

    protected void checkGlobalTag(Property property, Node node, Object object) {
        if (!object.getClass().isArray() || !object.getClass().getComponentType().isPrimitive()) {
            Class[] arguments = property.getActualTypeArguments();

            if (arguments != null) {
                Class keyType;
                Iterator i$;
                Iterator tuple;

                if (node.getNodeId() == NodeId.sequence) {
                    keyType = arguments[0];
                    SequenceNode valueType = (SequenceNode) node;
                    Object mnode = Collections.EMPTY_LIST;

                    if (object.getClass().isArray()) {
                        mnode = Arrays.asList((Object[]) ((Object[]) object));
                    } else if (object instanceof Iterable) {
                        mnode = (Iterable) object;
                    }

                    i$ = ((Iterable) mnode).iterator();
                    if (i$.hasNext()) {
                        tuple = valueType.getValue().iterator();

                        while (tuple.hasNext()) {
                            Node member = (Node) tuple.next();
                            Object tuple1 = i$.next();

                            if (tuple1 != null && keyType.equals(tuple1.getClass()) && member.getNodeId() == NodeId.mapping) {
                                member.setTag(Tag.MAP);
                            }
                        }
                    }
                } else if (object instanceof Set) {
                    keyType = arguments[0];
                    MappingNode valueType1 = (MappingNode) node;
                    Iterator mnode1 = valueType1.getValue().iterator();
                    Set i$1 = (Set) object;

                    tuple = i$1.iterator();

                    while (tuple.hasNext()) {
                        Object member1 = tuple.next();
                        NodeTuple tuple3 = (NodeTuple) mnode1.next();
                        Node keyNode = tuple3.getKeyNode();

                        if (keyType.equals(member1.getClass()) && keyNode.getNodeId() == NodeId.mapping) {
                            keyNode.setTag(Tag.MAP);
                        }
                    }
                } else if (object instanceof Map) {
                    keyType = arguments[0];
                    Class valueType2 = arguments[1];
                    MappingNode mnode2 = (MappingNode) node;

                    i$ = mnode2.getValue().iterator();

                    while (i$.hasNext()) {
                        NodeTuple tuple2 = (NodeTuple) i$.next();

                        this.resetTag(keyType, tuple2.getKeyNode());
                        this.resetTag(valueType2, tuple2.getValueNode());
                    }
                }
            }

        }
    }

    private void resetTag(Class type, Node node) {
        Tag tag = node.getTag();

        if (tag.matches(type)) {
            if (Enum.class.isAssignableFrom(type)) {
                node.setTag(Tag.STR);
            } else {
                node.setTag(Tag.MAP);
            }
        }

    }

    protected Set getProperties(Class type) {
        return this.typeDefinitions.containsKey(type) ? ((TypeDescription) this.typeDefinitions.get(type)).getProperties() : this.getPropertyUtils().getProperties(type);
    }

    protected class RepresentJavaBean implements Represent {

        public Node representData(Object data) {
            return Representer.this.representJavaBean(Representer.this.getProperties(data.getClass()), data);
        }
    }
}
