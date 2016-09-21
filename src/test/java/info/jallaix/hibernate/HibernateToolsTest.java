package info.jallaix.hibernate;

import info.jallaix.hibernate.domain.ChildEntity;
import info.jallaix.hibernate.domain.MainEntity;
import info.jallaix.hibernate.domain.ThroughEntity;
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

import java.lang.reflect.Field;
import java.util.*;

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

    /**
     * Verify an entity with only the identifier set is returned after un-proxying an uninitialized entity.
     */
    @Test
    public void unproxyUninitializedProxy() {

        // Fixture
        Pair<MainEntity, List<Queue<Field>>> fixture = buildMinimalFixture();

        // Load an uninitialized proxy
        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();

        MainEntity mainEntity = session.load(MainEntity.class, 1);

        tx.commit();
        session.close();

        // entity with only the identifier set expected after un-proxying
        Pair<MainEntity, List<Queue<Field>>> result = HibernateTools.unproxyDetachedRecursively(mainEntity);
        assertThat(result, is(fixture));
    }

    /**
     * Verify a full graph of POJO is returned after un-proxying a fully initialized entity.
     */
    @Test
    public void unproxyFullyInitializedProxies() {

        // Fixture
        Pair<MainEntity, List<Queue<Field>>> fixture = buildFullFixture();

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
        Pair<MainEntity, List<Queue<Field>>> result = HibernateTools.unproxyDetachedRecursively(mainEntity);
        assertThat(result, is(fixture));
    }

    /**
     * Verify a partial graph of POJO is returned after un-proxying a partially initialized entity.
     */
    @Test
    public void unproxyPartiallyInitializedProxies() {

        // Fixture
        Pair<MainEntity, List<Queue<Field>>> fixture = buildPartialFixture();

        // Load an initialized proxy
        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();

        MainEntity mainEntity = session.get(MainEntity.class, 1);

        tx.commit();
        session.close();

        // Full graph of POJO expected after un-proxying
        Pair<MainEntity, List<Queue<Field>>> result = HibernateTools.unproxyDetachedRecursively(mainEntity);
        assertThat(result, is(fixture));
    }

    @Test(expected = LazyInitializationException.class)
    public void createProxy() {

        ChildEntity childEntity = HibernateTools.createLazyProxy(ChildEntity.class);
        childEntity.getLabel();
    }


    /**
     * Build an entity of {@link MainEntity} type with all linked entities.
     *
     * @return An fully initialized entity
     */
    private Pair<MainEntity, List<Queue<Field>>>  buildFullFixture() {

        MainEntity mainEntity = buildPartialFixture().getLeft();
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

        return new ImmutablePair<>(mainEntity, new ArrayList<>());
    }

    /**
     * Build an entity of {@link MainEntity} type with partial linked entities.
     *
     * @return An partially initialized entity
     */
    private Pair<MainEntity, List<Queue<Field>>> buildPartialFixture() {

        MainEntity mainEntity = new MainEntity();
        mainEntity.setId(1);
        mainEntity.setLabel("mainEntity");

        List<Queue<Field>> uninitializedProxyPaths = new ArrayList<>();

        Queue<Field> fieldsPath = new LinkedList<>();
        fieldsPath.offer(null);
        try {
            fieldsPath.offer(MainEntity.class.getDeclaredField("throughEntities"));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

        uninitializedProxyPaths.add(fieldsPath);

        return new ImmutablePair<>(mainEntity, uninitializedProxyPaths);
    }

    private Pair<MainEntity, List<Queue<Field>>> buildMinimalFixture() {

        MainEntity mainEntity = new MainEntity();
        mainEntity.setId(1);

        List<Queue<Field>> uninitializedProxyPaths = new ArrayList<>();

        Queue<Field> fieldsPath = new LinkedList<>();
        fieldsPath.offer(null);

        uninitializedProxyPaths.add(fieldsPath);

        return new ImmutablePair<>(mainEntity, uninitializedProxyPaths);
    }
}
