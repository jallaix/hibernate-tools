package info.jallaix.hibernate;

import info.jallaix.hibernate.proxy.MockMethodFilter;
import info.jallaix.hibernate.proxy.MockMethodHandler;
import javassist.util.proxy.ProxyFactory;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.dozer.DozerBeanMapper;
import org.hibernate.Hibernate;
import org.hibernate.collection.internal.*;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.pojo.javassist.JavassistProxyFactory;

import javax.persistence.Id;
import java.lang.reflect.*;
import java.util.*;

/**
 * Utility class for Hibernate containing the method :
 * <ul>
 * <li>{@link #unproxyDetachedRecursively(Object)} : Remove all proxies from an object graph and keep uninitialized proxy paths.</li>
 * <li>{@link #feedWithMockedProxies(Pair)} : Add mock proxies to an object graph based on uninitialized proxy paths.</li>
 * </ul>
 */
public class HibernateTools {

    /**
     * Replace Hibernate proxies by POJOs in an object graph.
     *
     * @param <T>        Class type
     * @param maybeProxy Object that may contain Hibernate proxies
     * @return Object graph without Hibernate proxies, and paths to uninitialized proxies.
     * A path only holding a {@code null} value means the root object is uninitialized, thus no other path should be defined.
     */
    @SuppressWarnings("unused")
    public static <T> Pair<T, Set<LinkedList<Field>>> unproxyDetachedRecursively(T maybeProxy) {

        if (maybeProxy == null) {
            return null;
        }

        LinkedList<Field> fieldPath = new LinkedList<>();
        fieldPath.add(null);                             // Null indicates root object

        return unproxyDetachedRecursively(maybeProxy, new HashSet<>(), new HashSet<>(), fieldPath);
    }

    /**
     * Feed an object graph with mock Hibernate proxies so as to get {@code LazyInitializationException} as expected in the original graph.
     *
     * @param entityWithUninitializedProxyPaths Entity graph composed of POJOs
     * @param <T>                               Root entity type
     * @param <P>                               Generated proxy type
     * @return An entity graph with mock proxies
     */
    @SuppressWarnings({"unused", "unchecked"})
    public static <T, P extends T> P feedWithMockedProxies(final Pair<T, Set<LinkedList<Field>>> entityWithUninitializedProxyPaths) {

        // Get the root entity and the set of uninitialized proxy paths
        T entity = new DozerBeanMapper().map(entityWithUninitializedProxyPaths.getLeft(), (Class<T>) entityWithUninitializedProxyPaths.getLeft().getClass());
        Set<LinkedList<Field>> uninitializedProxyPaths = new HashSet<>();
        for (LinkedList<Field> uninitializedProxyPath : entityWithUninitializedProxyPaths.getRight()) {
            uninitializedProxyPaths.add(new LinkedList<>(uninitializedProxyPath));
        }

        // Return the original entity if no mock proxy has to be instantiated
        if (uninitializedProxyPaths.isEmpty())
            return (P) entity;

        // Each field path is linked to an uninitialized proxy
        P proxiedEntity = null;
        for (LinkedList<Field> fieldPath : uninitializedProxyPaths) {

            if (proxiedEntity == null)
                proxiedEntity = generateMockProxyForFieldPath(entity, fieldPath);
            else
                proxiedEntity = generateMockProxyForFieldPath(proxiedEntity, fieldPath);
        }

        return proxiedEntity;
    }

    /**
     * Generate a mock Hibernate proxy when the field path is reached
     *
     * @param entity    Entity for which a mock proxy could be
     * @param fieldPath Remaining field path for the mock proxy
     * @param <T>       Entity type
     * @param <P>       Generated proxy type
     * @return An entity graph with mock proxies
     */
    @SuppressWarnings("unchecked")
    private static <T, P extends T> P generateMockProxyForFieldPath(T entity, LinkedList<Field> fieldPath) {

        // Remove the first field from the list
        final Field firstField = fieldPath.removeFirst();

        final Object fieldValue;
        final Class fieldClazz;

        // A null field indicates the root entity
        if (firstField == null) {
            fieldValue = entity;
            fieldClazz = entity.getClass();
        }

        // Set/List association : determine the proxy for each item
        else if (Collection.class.isAssignableFrom(entity.getClass())) {

            Collection entitySet = (Collection) entity;
            Collection proxiedSet;
            try {
                proxiedSet = entitySet.getClass().newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            for (Object item : entitySet) {
                fieldPath.addFirst(firstField);
                proxiedSet.add(generateMockProxyForFieldPath(item, fieldPath));
            }
            return (P) proxiedSet;
        }

        // Map association : determine the proxy for each key and value
        else if (Map.class.isAssignableFrom(entity.getClass())) {

            Map entityMap = (Map) entity;
            Map proxiedMap;
            try {
                proxiedMap = entityMap.getClass().newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            for (Object entryObject : entityMap.entrySet()) {
                Map.Entry entry = (Map.Entry) entryObject;
                fieldPath.addFirst(firstField);
                Object proxiedKey = generateMockProxyForFieldPath(entry.getKey(), fieldPath);
                fieldPath.addFirst(firstField);
                Object proxiedValue = generateMockProxyForFieldPath(entry.getValue(), fieldPath);

                proxiedMap.put(proxiedKey, proxiedValue);
            }
            return (P) proxiedMap;
        }

        // Array association : determine the proxy for each item
        else if (Array.class.isAssignableFrom(entity.getClass())) {

            Object[] entityArray = (Object[]) entity;
            Object[] proxiedArray;
            try {
                proxiedArray = entityArray.getClass().newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            for (int i = 0; i < entityArray.length; i++) {
                fieldPath.addFirst(firstField);
                proxiedArray[i] = generateMockProxyForFieldPath(entityArray[i], fieldPath);
            }
            return (P) proxiedArray;
        }

        // Other cases : get the field value
        else {
            firstField.setAccessible(true);
            fieldClazz = firstField.getType();
            try {
                fieldValue = firstField.get(entity);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        // Generate a proxy for the field value
        final Object proxiedFieldValue;
        if (fieldPath.isEmpty()) {

            if (Set.class.isAssignableFrom(fieldClazz))
                proxiedFieldValue = new PersistentSet();
            else if (Map.class.isAssignableFrom(fieldClazz))
                proxiedFieldValue = new PersistentMap();
            else if (List.class.isAssignableFrom(fieldClazz))
                proxiedFieldValue = new PersistentList();
            else if (Array.class.isAssignableFrom(fieldClazz))
                proxiedFieldValue = new PersistentArrayHolder(null, new Object());
            else
                proxiedFieldValue = createLazyProxy(fieldValue, fieldClazz);
        }

        // The proxy will be found further in the object graph
        else
            proxiedFieldValue = generateMockProxyForFieldPath(fieldValue, fieldPath);

        //
        if (firstField == null)
            return (P) proxiedFieldValue;

        else {
            try {
                firstField.set(entity, proxiedFieldValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            return (P) entity;
        }
    }

    /**
     * Recursively replace Hibernate proxies by POJOs.
     *
     * @param <T>                     Class type
     * @param maybeProxy              Object that may contain Hibernate proxies
     * @param uninitializedProxyPaths Set of uninitialized proxy paths
     * @param visited                 List of objects that have already been un-proxied
     * @param fieldPath               Queue of {@code Field}s representing the depth in graph
     * @return Object graph without Hibernate proxies, and paths to uninitialized proxies.
     */
    private static <T> Pair<T, Set<LinkedList<Field>>> unproxyDetachedRecursively(T maybeProxy, Set<LinkedList<Field>> uninitializedProxyPaths, HashSet<Object> visited, LinkedList<Field> fieldPath) {

        if (maybeProxy == null) {
            return null;
        }

        // Get the class to un-proxy
        Class<T> clazz = getUnproxyClass(maybeProxy);

        // Un-proxy the bean
        Pair<T, Boolean> unproxyResult = getPojoFromProxy(maybeProxy, clazz);
        T unproxiedEntity = unproxyResult.getLeft();

        // Add the field path to the list of uninitialized proxy paths if the un-proxied bean is uninitialized
        if (!unproxyResult.getRight()) {
            uninitializedProxyPaths.add(new LinkedList<>(fieldPath));
            return new ImmutablePair<>(unproxiedEntity, uninitializedProxyPaths);
        }

        // Return if the bean is already un-proxied
        if (visited.contains(unproxiedEntity)) {
            return new ImmutablePair<>(unproxiedEntity, uninitializedProxyPaths);
        } else {
            visited.add(unproxiedEntity);
        }

        // Un-proxy contained fields
        for (Field field : clazz.getDeclaredFields()) {

            // Final fields aren't touched
            if ((field.getModifiers() & Modifier.FINAL) == Modifier.FINAL) {
                continue;
            }

            // Add the current field to the field path
            fieldPath.addLast(field);

            // Get the field value
            Object fieldValue;
            try {
                field.setAccessible(true);
                fieldValue = field.get(unproxiedEntity);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            // Un-proxy an proxied collection field
            if (fieldValue instanceof AbstractPersistentCollection && !((AbstractPersistentCollection) fieldValue).wasInitialized()) {
                fieldValue = null;
                uninitializedProxyPaths.add(new LinkedList<>(fieldPath));
            }
            // Un-proxy an array
            else if (fieldValue instanceof Object[]) {

                Object[] valueArray = (Object[]) fieldValue;
                Object[] result = (Object[]) Array.newInstance(fieldValue.getClass(), valueArray.length);
                for (int i = 0; i < valueArray.length; i++) {

                    Pair<Object, Set<LinkedList<Field>>> itemUnproxyResult = unproxyDetachedRecursively(valueArray[i], uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = itemUnproxyResult.getRight();

                    result[i] = itemUnproxyResult.getLeft();
                }
                fieldValue = result;
            }
            // Un-proxy a set
            else if (fieldValue instanceof Set) {

                Set<?> valueSet = (Set<?>) fieldValue;
                Set<Object> result = new HashSet<>();
                for (Object o : valueSet) {

                    Pair<Object, Set<LinkedList<Field>>> itemUnproxyResult = unproxyDetachedRecursively(o, uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = itemUnproxyResult.getRight();

                    result.add(itemUnproxyResult.getLeft());
                }
                fieldValue = result;
            }
            // Un-proxy a map
            else if (fieldValue instanceof Map) {

                Map<?, ?> valueMap = (Map<?, ?>) fieldValue;
                Map<Object, Object> result = new HashMap<>();
                for (Object o : valueMap.keySet()) {

                    Pair<Object, Set<LinkedList<Field>>> keyUnproxyResult = unproxyDetachedRecursively(o, uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = keyUnproxyResult.getRight();
                    Pair<Object, Set<LinkedList<Field>>> valueUnproxyResult = unproxyDetachedRecursively(valueMap.get(o), uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = valueUnproxyResult.getRight();

                    result.put(keyUnproxyResult.getLeft(), valueUnproxyResult.getLeft());
                }
                fieldValue = result;
            }
            // Un-proxy a list
            else if (fieldValue instanceof List) {

                List<?> valueList = (List<?>) fieldValue;
                List<Object> result = new ArrayList<>(valueList.size());
                for (Object o : valueList) {

                    Pair<Object, Set<LinkedList<Field>>> itemUnproxyResult = unproxyDetachedRecursively(o, uninitializedProxyPaths, visited, fieldPath);
                    uninitializedProxyPaths = itemUnproxyResult.getRight();

                    result.add(itemUnproxyResult.getLeft());
                }
                fieldValue = result;
            }
            // Un-proxy another field type
            else {
                Pair<Object, Set<LinkedList<Field>>> fieldUnproxyResult = unproxyDetachedRecursively(fieldValue, uninitializedProxyPaths, visited, fieldPath);
                uninitializedProxyPaths = fieldUnproxyResult.getRight();

                fieldValue = fieldUnproxyResult.getLeft();
            }

            // Replace the field value by the un-proxied value
            try {
                field.set(unproxiedEntity, fieldValue);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            // Remove the current field from the field path
            fieldPath.removeLast();
        }

        return new ImmutablePair<>(unproxiedEntity, uninitializedProxyPaths);
    }

    /**
     * Get the base class of an entity to un-proxy.
     *
     * @param <T>        Class type
     * @param maybeProxy Object that may contain Hibernate proxies
     * @return Base class of the entity to un-proxy
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> getUnproxyClass(final T maybeProxy) {

        Class<T> clazz;
        if (maybeProxy instanceof HibernateProxy) {

            HibernateProxy proxy = (HibernateProxy) maybeProxy;
            if (Hibernate.isInitialized(proxy))
                clazz = (Class<T>) proxy.getHibernateLazyInitializer().getImplementation().getClass();
            else
                clazz = (Class<T>) proxy.getHibernateLazyInitializer().getPersistentClass();
        } else {
            clazz = (Class<T>) maybeProxy.getClass();
        }

        return clazz;
    }

    /**
     * Get the POJO matching an Hibernate entity:
     * <ol>
     * <li>The entity itself if the entity isn't a proxy.</li>
     * <li>The entity implementation if the entity is an initialized proxy.</li>
     * <li>A new entity with its identifier set if the entity is an uninitialized proxy.</li>
     * </ol>
     *
     * @param <T>        Class type
     * @param maybeProxy Object that may contain an Hibernate proxy
     * @param baseClass  Proxy base class
     * @return A POJO and its proxy initialisation state ({@code true} if initialized)
     */
    private static <T> Pair<T, Boolean> getPojoFromProxy(final T maybeProxy, final Class<T> baseClass) {

        if (maybeProxy == null) {
            return null;
        }

        if (maybeProxy instanceof HibernateProxy) {

            if (Hibernate.isInitialized(maybeProxy))    // Linked POJO is returned if there is an initialized proxy

                return new ImmutablePair<>(
                        baseClass.cast(((HibernateProxy) maybeProxy).getHibernateLazyInitializer().getImplementation()),
                        true);

            else {  // POJO built with identifier is returned if there is an uninitialized proxy

                // Find the identifier field in the entity class
                Field identifierField = findIdentifierField(baseClass);
                Method identifierSetter = null;
                if (identifierField == null) {
                    identifierSetter = getSetter(findIdentifierGetter(baseClass));
                }

                // Create an entity bean initialized with the proxy's identifier
                final T uninitializedEntity;
                try {
                    uninitializedEntity = baseClass.newInstance();
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

                // Initialize the identifier
                try {
                    if (identifierField != null)
                        identifierField.set(uninitializedEntity, ((HibernateProxy) maybeProxy).getHibernateLazyInitializer().getIdentifier());
                    else if (identifierSetter != null)
                        identifierSetter.invoke(uninitializedEntity, ((HibernateProxy) maybeProxy).getHibernateLazyInitializer().getIdentifier());
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }

                return new ImmutablePair<>(uninitializedEntity, false);
            }
        }
        // Original POJO is returned if there is no proxy
        else
            return new ImmutablePair<>(baseClass.cast(maybeProxy), true);
    }

    /**
     * Find the field used as identifier for the entity.
     *
     * @param <T>    The entity type
     * @param entity The entity class
     * @return The identifier field
     */
    private static <T> Field findIdentifierField(Class<T> entity) {

        // Find field in entity class with @Id annotation
        for (Field field : entity.getDeclaredFields()) {
            if (field.getDeclaredAnnotation(Id.class) != null) {
                field.setAccessible(true);
                return field;
            }
        }

        return null;
    }

    /**
     * Find the identifier getter method for the entity.
     *
     * @param <T>    The entity type
     * @param entity The entity class
     * @return The identifier getter method
     */
    private static <T> Method findIdentifierGetter(Class<T> entity) {

        // Find method in entity class with @Id annotation
        for (Method method : entity.getDeclaredMethods()) {
            if (method.getDeclaredAnnotation(Id.class) != null) {
                method.setAccessible(true);
                return method;
            }
        }

        return null;
    }

    /**
     * Find a setter method linked to a getter method.
     *
     * @param getter The getter method
     * @return The setter method found
     */
    private static Method getSetter(Method getter) {

        final String setterName = getter.getName().replaceFirst("get", "set");
        for (Method method : getter.getDeclaringClass().getDeclaredMethods()) {
            if (method.getName().equals(setterName)) {
                method.setAccessible(true);
                return method;
            }
        }

        return null;
    }

    /**
     * Create a proxy that throws {@code LazyInitializationException} for every accessor method except the identifier one.
     *
     * @param entity Entity for which the proxy has to be instantiated (may be null)
     * @param clazz  Entity class
     * @param <T>    Entity type
     * @param <P>    Proxy type
     * @return An newly instantiated proxy
     */
    @SuppressWarnings("unchecked")
    public static <T, P extends T> P createLazyProxy(final T entity, Class clazz) {

        final P proxy;

        // Find the identifier getter method
        final Method identifierGetter;
        final Field identifierField = findIdentifierField(clazz);
        if (identifierField == null)
            identifierGetter = findIdentifierGetter(clazz);
        else {
            String firstChar = identifierField.getName().substring(0, 1).toUpperCase();
            String lastChars = identifierField.getName().substring(1);
            Method tempIdentifierGetter = null;
            try {
                tempIdentifierGetter = clazz.getDeclaredMethod("get" + firstChar + lastChars);
            } catch (NoSuchMethodException ignored) {
            } finally {
                identifierGetter = tempIdentifierGetter;
            }
        }

        // Build proxy
        ProxyFactory proxyFactory = JavassistProxyFactory.buildJavassistProxyFactory(clazz, new Class[]{HibernateProxy.class});
        proxyFactory.setSuperclass(clazz);
        proxyFactory.setInterfaces(new Class[]{HibernateProxy.class});
        proxyFactory.setFilter(new MockMethodFilter());
        try {
            //noinspection unchecked
            proxy = (P) proxyFactory.create(
                    new Class[0],
                    new Object[0],
                    new MockMethodHandler(
                            entity,
                            identifierGetter == null ? null : identifierGetter.getName()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return proxy;
    }
}
