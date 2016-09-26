package info.jallaix.hibernate;

import info.jallaix.hibernate.domain.ChildEntity;
import info.jallaix.hibernate.domain.MainEntity;
import info.jallaix.hibernate.domain.ThroughEntity;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.hibernate.*;
import org.hibernate.cfg.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.util.*;

import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Test Hibernate utility methods.
 */
public class HibernateToolsTest {

    /**
     * DbUnit database configuration
     */
    private IDatabaseTester databaseTester;

    /**
     * Hibernate session factory
     */
    private SessionFactory sessionFactory;

    /**
     * Initialize Hibernate framework (create database structure) and load fixture data using DbUnit.
     *
     * @throws Exception If Hibernate framework or DbUnit data loading fails
     */
    @Before
    public void init() throws Exception {

        Configuration config = new Configuration()
                .addAnnotatedClass(MainEntity.class)
                .addAnnotatedClass(ThroughEntity.class)
                .addAnnotatedClass(ChildEntity.class);
        sessionFactory = config.buildSessionFactory();

        databaseTester = new JdbcDatabaseTester("org.h2.Driver", "jdbc:h2:mem:test", "", "");
        IDataSet dataSet = new FlatXmlDataSetBuilder().build(this.getClass().getClassLoader().getResourceAsStream("dataset.xml"));
        databaseTester.setDataSet(dataSet);
        databaseTester.onSetup();
    }

    /**
     * Remove fixture data set by DbUnit.
     *
     * @throws Exception If DbUnit data deleting fails
     */
    @After
    public void exit() throws Exception {
        databaseTester.onTearDown();
    }


    /*----------------------------------------------------------------------------------------------------------------*/
    /*                                                    Tests                                                       */
    /*----------------------------------------------------------------------------------------------------------------*/

    /**
     * Verify an entity with only the identifier set is returned after un-proxying an uninitialized entity.
     */
    @Test
    public void unproxyUninitializedEntity() {

        // Fixture
        Pair<MainEntity, Set<LinkedList<Field>>> fixture = buildUninitializedMainEntity();

        // Load an uninitialized proxy
        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();

        MainEntity mainEntity = session.load(MainEntity.class, 1);

        tx.commit();
        session.close();

        // entity with only the identifier set expected after un-proxying
        Pair<MainEntity, Set<LinkedList<Field>>> result = HibernateTools.unproxyDetachedRecursively(mainEntity);
        assertThat(result, is(fixture));
    }

    /**
     * Verify a partial graph of POJO is returned after un-proxying a partially initialized entity (no collection).
     */
    @Test
    public void unproxyEntityWithUninitializedCollection() {

        // Fixture
        Pair<MainEntity, Set<LinkedList<Field>>> fixture = buildMainEntityWithUninitializedThroughEntities();

        // Load an initialized proxy
        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();

        MainEntity mainEntity = session.get(MainEntity.class, 1);

        tx.commit();
        session.close();

        // Full graph of POJO expected after un-proxying
        Pair<MainEntity, Set<LinkedList<Field>>> result = HibernateTools.unproxyDetachedRecursively(mainEntity);
        assertThat(result, is(fixture));
    }

    /**
     * Verify a partial graph of POJO is returned after un-proxying a partially initialized entity (collection but no sub-entity).
     */
    @Test
    public void unproxyEntityWithUninitializedSubEntity() {

        // Fixture
        Pair<MainEntity, Set<LinkedList<Field>>> fixture = buildMainEntityWithUninitializedChildEntities();

        // Load an initialized proxy
        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();

        MainEntity mainEntity = session.get(MainEntity.class, 1);
        Hibernate.initialize(mainEntity.getThroughEntities());

        tx.commit();
        session.close();

        // Full graph of POJO expected after un-proxying
        Pair<MainEntity, Set<LinkedList<Field>>> result = HibernateTools.unproxyDetachedRecursively(mainEntity);
        assertThat(result, is(fixture));
    }

    /**
     * Verify a full graph of POJO is returned after un-proxying a fully initialized entity (collection and no sub-entity).
     */
    @Test
    public void unproxyFullyInitializedEntity() {

        // Fixture
        Pair<MainEntity, Set<LinkedList<Field>>> fixture = buildFullyInitializedMainEntity();

        // Load main, through et child entities
        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();

        MainEntity mainEntity = session.load(MainEntity.class, 1);
        Hibernate.initialize(mainEntity.getThroughEntities());
        Set<ThroughEntity> throughEntities = mainEntity.getThroughEntities();
        throughEntities.forEach(t -> Hibernate.initialize(t.getChildEntity()));

        tx.commit();
        session.close();

        // Full graph of POJO expected after un-proxying
        Pair<MainEntity, Set<LinkedList<Field>>> result = HibernateTools.unproxyDetachedRecursively(mainEntity);
        assertThat(result, is(fixture));
    }

    /**
     * Verify a previously uninitialized entity is properly feed with proxy mocks.
     */
    @Test
    public void feedUninitializedEntityWithProxyMocks() {

        // Entity with proxy paths
        Pair<MainEntity, Set<LinkedList<Field>>> unproxiedEntity = buildUninitializedMainEntity();

        MainEntity entityMocked = HibernateTools.feedWithMockedProxies(unproxiedEntity);
        assertThat(entityMocked.getId(), is(unproxiedEntity.getLeft().getId()));

        try {
            entityMocked.getLabel();
            fail("A LazyInitializationException should be thrown");
        } catch (LazyInitializationException ignored) {
        }
    }

    /**
     * Verify an entity with a previously uninitialized collection is properly feed with proxy mocks.
     */
    @Test
    public void feedEntityWithUninitializedCollectionWithProxyMocks() {

        // Mock entity with proxy
        Pair<MainEntity, Set<LinkedList<Field>>> unproxiedEntity = buildMainEntityWithUninitializedThroughEntities();

        MainEntity mainEntityMocked = HibernateTools.feedWithMockedProxies(unproxiedEntity);
        assertThat(mainEntityMocked.getId(), is(unproxiedEntity.getLeft().getId()));
        assertThat(mainEntityMocked.getLabel(), is(unproxiedEntity.getLeft().getLabel()));

        try {
            mainEntityMocked.getThroughEntities().iterator();
            fail("A LazyInitializationException should be thrown");
        } catch (LazyInitializationException ignored) {
        }
    }

    /**
     * Verify an entity with previously uninitialized sub-entities is properly feed with proxy mocks.
     */
    @Test
    public void feedEntityWithUninitializedSubEntityWithProxyMocks() {

        // Mock entity with proxy
        Pair<MainEntity, Set<LinkedList<Field>>> unproxiedEntity = buildMainEntityWithUninitializedChildEntities();

        MainEntity mainEntityMocked = HibernateTools.feedWithMockedProxies(unproxiedEntity);
        assertThat(mainEntityMocked.getId(), is(unproxiedEntity.getLeft().getId()));
        assertThat(mainEntityMocked.getLabel(), is(unproxiedEntity.getLeft().getLabel()));

        for (ThroughEntity throughEntityMocked : mainEntityMocked.getThroughEntities()) {

            assertThat(throughEntityMocked.getId(), is(notNullValue()));
            assertThat(throughEntityMocked.getLabel(), is(notNullValue()));

            assertThat(throughEntityMocked.getChildEntity().getId(), is(notNullValue()));
            try {
                throughEntityMocked.getChildEntity().getLabel();
                fail("A LazyInitializationException should be thrown");
            } catch (LazyInitializationException ignored) {
            }
        }
    }

    /**
     * Verify an entity with previously no uninitialized sub-entities is not fed with proxy mocks.
     */
    @Test
    public void feedFullyInitializedEntityWithProxyMocks() {

        // Mock entity with proxy
        Pair<MainEntity, Set<LinkedList<Field>>> unproxiedEntity = buildFullyInitializedMainEntity();

        MainEntity mainEntityMocked = HibernateTools.feedWithMockedProxies(unproxiedEntity);

        assertThat(mainEntityMocked, is(unproxiedEntity.getLeft()));
    }

    /**
     * Verify an entity with mocked proxies is serializable
     */
    @Test
    @SneakyThrows
    public void serializeProxyMocks() {

        Pair<MainEntity, Set<LinkedList<Field>>> unproxiedEntity = buildMainEntityWithUninitializedChildEntities();

        MainEntity mainEntityMocked = HibernateTools.feedWithMockedProxies(unproxiedEntity);

        try (ObjectOutputStream out = new ObjectOutputStream(new ByteArrayOutputStream())) {
            out.writeObject(mainEntityMocked);
        }
    }

    /**
     * Un-proxying an entity with mocked proxies acts the same as with Hibernate proxies
     */
    @Test
    public void unproxyMockedProxies() {

        Pair<MainEntity, Set<LinkedList<Field>>> unproxiedEntity = buildMainEntityWithUninitializedChildEntities();

        MainEntity mainEntityMocked = HibernateTools.feedWithMockedProxies(unproxiedEntity);
        Pair<MainEntity, Set<LinkedList<Field>>> unproxiedEntityUnmocked = HibernateTools.unproxyDetachedRecursively(mainEntityMocked);

        assertThat(unproxiedEntityUnmocked, is(unproxiedEntity));
    }


    /*----------------------------------------------------------------------------------------------------------------*/
    /*                                                  Fixtures                                                      */
    /*----------------------------------------------------------------------------------------------------------------*/

    /**
     * Build an uninitialized {@link MainEntity}.
     *
     * @return An uninitialized entity
     */
    private Pair<MainEntity, Set<LinkedList<Field>>> buildUninitializedMainEntity() {

        // Entity
        MainEntity mainEntity = new MainEntity();
        mainEntity.setId(1);

        // mainEntity was an uninitialized proxy
        Set<LinkedList<Field>> uninitializedProxyPaths = new HashSet<>();
        LinkedList<Field> fieldsPath = new LinkedList<>();
        fieldsPath.addLast(null);
        uninitializedProxyPaths.add(fieldsPath);

        return new ImmutablePair<>(mainEntity, uninitializedProxyPaths);
    }

    /**
     * Build a {@link MainEntity} with uninitialized {@link ThroughEntity}s.
     *
     * @return An partially initialized entity
     */
    private Pair<MainEntity, Set<LinkedList<Field>>> buildMainEntityWithUninitializedThroughEntities() {

        Pair<MainEntity, Set<LinkedList<Field>>> uninitializedMainEntity = buildUninitializedMainEntity();

        // Entity
        MainEntity mainEntity = uninitializedMainEntity.getLeft();
        mainEntity.setLabel("mainEntity");

        // mainEntity.throughEntities was an uninitialized proxy
        Set<LinkedList<Field>> uninitializedProxyPaths = uninitializedMainEntity.getRight();
        try {
            uninitializedProxyPaths.iterator().next().addLast(MainEntity.class.getDeclaredField("throughEntities"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        return new ImmutablePair<>(mainEntity, uninitializedProxyPaths);
    }

    /**
     * Build a {@link MainEntity} with {@link ThroughEntity}s and uninitialized {@link ChildEntity}s.
     *
     * @return An partially initialized entity
     */
    private Pair<MainEntity, Set<LinkedList<Field>>> buildMainEntityWithUninitializedChildEntities() {

        Pair<MainEntity, Set<LinkedList<Field>>> entityWithUninitializedThroughEntities = buildMainEntityWithUninitializedThroughEntities();

        // Entity
        MainEntity mainEntity = entityWithUninitializedThroughEntities.getLeft();
        mainEntity.setThroughEntities(new HashSet<>());

        ChildEntity childEntity1 = new ChildEntity();
        childEntity1.setId(1);

        ChildEntity childEntity2 = new ChildEntity();
        childEntity2.setId(2);

        ThroughEntity throughEntity1 = new ThroughEntity();
        throughEntity1.setId(1);
        throughEntity1.setLabel("through1");
        throughEntity1.setMainEntity(mainEntity);
        throughEntity1.setChildEntity(childEntity1);
        mainEntity.getThroughEntities().add(throughEntity1);

        ThroughEntity throughEntity2 = new ThroughEntity();
        throughEntity2.setId(2);
        throughEntity2.setLabel("through2");
        throughEntity2.setMainEntity(mainEntity);
        throughEntity2.setChildEntity(childEntity2);
        mainEntity.getThroughEntities().add(throughEntity2);

        ThroughEntity throughEntity3 = new ThroughEntity();
        throughEntity3.setId(3);
        throughEntity3.setLabel("through3");
        throughEntity3.setMainEntity(mainEntity);
        throughEntity3.setChildEntity(childEntity1);
        mainEntity.getThroughEntities().add(throughEntity3);

        // mainEntity.throughEntities.childEntity was an uninitialized proxy
        Set<LinkedList<Field>> uninitializedProxyPaths = entityWithUninitializedThroughEntities.getRight();
        try {
            uninitializedProxyPaths.iterator().next().addLast(ThroughEntity.class.getDeclaredField("childEntity"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        return new ImmutablePair<>(mainEntity, uninitializedProxyPaths);
    }

    /**
     * Build a {@link MainEntity} with all initialized linked entities.
     *
     * @return An fully initialized entity
     */
    private Pair<MainEntity, Set<LinkedList<Field>>> buildFullyInitializedMainEntity() {

        MainEntity mainEntity = buildMainEntityWithUninitializedThroughEntities().getLeft();
        mainEntity.setThroughEntities(new HashSet<>());

        ChildEntity childEntity1 = new ChildEntity();
        childEntity1.setId(1);
        childEntity1.setLabel("child1");

        ChildEntity childEntity2 = new ChildEntity();
        childEntity2.setId(2);
        childEntity2.setLabel("child2");

        ThroughEntity throughEntity1 = new ThroughEntity();
        throughEntity1.setId(1);
        throughEntity1.setLabel("through1");
        throughEntity1.setMainEntity(mainEntity);
        throughEntity1.setChildEntity(childEntity1);
        mainEntity.getThroughEntities().add(throughEntity1);

        ThroughEntity throughEntity2 = new ThroughEntity();
        throughEntity2.setId(2);
        throughEntity2.setLabel("through2");
        throughEntity2.setMainEntity(mainEntity);
        throughEntity2.setChildEntity(childEntity2);
        mainEntity.getThroughEntities().add(throughEntity2);

        ThroughEntity throughEntity3 = new ThroughEntity();
        throughEntity3.setId(3);
        throughEntity3.setLabel("through3");
        throughEntity3.setMainEntity(mainEntity);
        throughEntity3.setChildEntity(childEntity1);
        mainEntity.getThroughEntities().add(throughEntity3);

        return new ImmutablePair<>(mainEntity, new HashSet<>());
    }
}
