package org.yaml.snakeyaml.introspector;

import java.beans.FeatureDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.util.PlatformFeatureDetector;

public class PropertyUtils {

    private final Map propertiesCache;
    private final Map readableProperties;
    private BeanAccess beanAccess;
    private boolean allowReadOnlyProperties;
    private boolean skipMissingProperties;
    private PlatformFeatureDetector platformFeatureDetector;
    private static final String TRANSIENT = "transient";

    public PropertyUtils() {
        this(new PlatformFeatureDetector());
    }

    PropertyUtils(PlatformFeatureDetector platformFeatureDetector) {
        this.propertiesCache = new HashMap();
        this.readableProperties = new HashMap();
        this.beanAccess = BeanAccess.DEFAULT;
        this.allowReadOnlyProperties = false;
        this.skipMissingProperties = false;
        this.platformFeatureDetector = platformFeatureDetector;
        if (platformFeatureDetector.isRunningOnAndroid()) {
            this.beanAccess = BeanAccess.FIELD;
        }

    }

    protected Map getPropertiesMap(Class type, BeanAccess bAccess) {
        if (this.propertiesCache.containsKey(type)) {
            return (Map) this.propertiesCache.get(type);
        } else {
            LinkedHashMap properties;
            boolean inaccessableFieldsExist;

            properties = new LinkedHashMap();
            inaccessableFieldsExist = false;
            Class c;
            Field[] arr$;
            int len$;
            int i$;
            Field field;
            int modifiers;

            label91:
            switch (bAccess) {
            case FIELD:
                c = type;

                while (true) {
                    if (c == null) {
                        break label91;
                    }

                    arr$ = c.getDeclaredFields();
                    len$ = arr$.length;

                    for (i$ = 0; i$ < len$; ++i$) {
                        field = arr$[i$];
                        modifiers = field.getModifiers();
                        if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers) && !properties.containsKey(field.getName())) {
                            properties.put(field.getName(), new FieldProperty(field));
                        }
                    }

                    c = c.getSuperclass();
                }

            default:
                try {
                    PropertyDescriptor[] apropertydescriptor = Introspector.getBeanInfo(type).getPropertyDescriptors();
                    int i = apropertydescriptor.length;

                    for (len$ = 0; len$ < i; ++len$) {
                        PropertyDescriptor propertydescriptor = apropertydescriptor[len$];
                        Method method = propertydescriptor.getReadMethod();

                        if ((method == null || !method.getName().equals("getClass")) && !this.isTransient(propertydescriptor)) {
                            properties.put(propertydescriptor.getName(), new MethodProperty(propertydescriptor));
                        }
                    }
                } catch (IntrospectionException introspectionexception) {
                    throw new YAMLException(introspectionexception);
                }

                for (c = type; c != null; c = c.getSuperclass()) {
                    arr$ = c.getDeclaredFields();
                    len$ = arr$.length;

                    for (i$ = 0; i$ < len$; ++i$) {
                        field = arr$[i$];
                        modifiers = field.getModifiers();
                        if (!Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers)) {
                            if (Modifier.isPublic(modifiers)) {
                                properties.put(field.getName(), new FieldProperty(field));
                            } else {
                                inaccessableFieldsExist = true;
                            }
                        }
                    }
                }
            }

            if (properties.isEmpty() && inaccessableFieldsExist) {
                throw new YAMLException("No JavaBean properties found in " + type.getName());
            } else {
                this.propertiesCache.put(type, properties);
                return properties;
            }
        }
    }

    private boolean isTransient(FeatureDescriptor fd) {
        return Boolean.TRUE.equals(fd.getValue("transient"));
    }

    public Set getProperties(Class type) {
        return this.getProperties(type, this.beanAccess);
    }

    public Set getProperties(Class type, BeanAccess bAccess) {
        if (this.readableProperties.containsKey(type)) {
            return (Set) this.readableProperties.get(type);
        } else {
            Set properties = this.createPropertySet(type, bAccess);

            this.readableProperties.put(type, properties);
            return properties;
        }
    }

    protected Set createPropertySet(Class type, BeanAccess bAccess) {
        TreeSet properties = new TreeSet();
        Collection props = this.getPropertiesMap(type, bAccess).values();
        Iterator i$ = props.iterator();

        while (i$.hasNext()) {
            Property property = (Property) i$.next();

            if (property.isReadable() && (this.allowReadOnlyProperties || property.isWritable())) {
                properties.add(property);
            }
        }

        return properties;
    }

    public Property getProperty(Class type, String name) {
        return this.getProperty(type, name, this.beanAccess);
    }

    public Property getProperty(Class type, String name, BeanAccess bAccess) {
        Map properties = this.getPropertiesMap(type, bAccess);
        Object property = (Property) properties.get(name);

        if (property == null && this.skipMissingProperties) {
            property = new MissingProperty(name);
        }

        if (property == null) {
            throw new YAMLException("Unable to find property \'" + name + "\' on class: " + type.getName());
        } else {
            return (Property) property;
        }
    }

    public void setBeanAccess(BeanAccess beanAccess) {
        if (this.platformFeatureDetector.isRunningOnAndroid() && beanAccess != BeanAccess.FIELD) {
            throw new IllegalArgumentException("JVM is Android - only BeanAccess.FIELD is available");
        } else {
            if (this.beanAccess != beanAccess) {
                this.beanAccess = beanAccess;
                this.propertiesCache.clear();
                this.readableProperties.clear();
            }

        }
    }

    public void setAllowReadOnlyProperties(boolean allowReadOnlyProperties) {
        if (this.allowReadOnlyProperties != allowReadOnlyProperties) {
            this.allowReadOnlyProperties = allowReadOnlyProperties;
            this.readableProperties.clear();
        }

    }

    public boolean isAllowReadOnlyProperties() {
        return this.allowReadOnlyProperties;
    }

    public void setSkipMissingProperties(boolean skipMissingProperties) {
        if (this.skipMissingProperties != skipMissingProperties) {
            this.skipMissingProperties = skipMissingProperties;
            this.readableProperties.clear();
        }

    }

    public boolean isSkipMissingProperties() {
        return this.skipMissingProperties;
    }

    static class SyntheticClass_1 {

    }
}
