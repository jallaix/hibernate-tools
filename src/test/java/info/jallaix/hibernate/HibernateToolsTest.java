package info.jallaix.hibernate;

import info.jallaix.hibernate.domain.ChildEntity;
import info.jallaix.hibernate.domain.MainEntity;
import info.jallaix.hibernate.domain.ThroughEntity;
import org.dbunit.IDatabaseTester;
import org.dbunit.JdbcDatabaseTester;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;

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
     * Verify no Hibernate proxy exist in the entity graph after the unproxy function executes.
     */
    @Test
    public void unproxyDetachedRecursivelyRemovesProxies() {

        Session session = sessionFactory.openSession();
        Transaction tx = session.beginTransaction();

        // Load main, through et child entities
        MainEntity mainEntity = session.load(MainEntity.class, 1);
        MainEntity resultEntity = HibernateTools.unproxyDetachedRecursively(mainEntity);
        Hibernate.initialize(mainEntity.getThroughEntities());
        Set<ThroughEntity> throughEntities = mainEntity.getThroughEntities();
        throughEntities.forEach(t -> Hibernate.initialize(t.getChildEntity()));

        tx.commit();
        session.close();

        MainEntity resultEntity2 = HibernateTools.unproxyDetachedRecursively(mainEntity);
    }
}
