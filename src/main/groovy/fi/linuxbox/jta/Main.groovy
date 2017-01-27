package fi.linuxbox.jta

import bitronix.tm.TransactionManagerServices
import bitronix.tm.resource.jdbc.PoolingDataSource
import groovy.util.logging.Slf4j

import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.Persistence
import javax.transaction.UserTransaction

@Slf4j
class Main {
    static void main(String... args) {
        // set the allowLocalTransactions=true to enable the
        // drop-and-create action from persistence.xml.
        log.trace("Initializing (JTA) Data Sources")
        initDerbyDataSource("users1", "jdbc/testDS1", false)
        initDerbyDataSource("users2", "jdbc/testDS2", false)

        log.trace("Initializing (JPA) Entity Manager Factories")
        EntityManagerFactory emf1 = Persistence.createEntityManagerFactory("testPU1")
        EntityManagerFactory emf2 = Persistence.createEntityManagerFactory("testPU2")

        try {
            createUserAndMoveUsers(emf1, emf2)
        } finally {
            log.trace("Closing (JPA) Entity Manager Factories")
            emf1.close()
            emf2.close()
            log.trace("Closing (JTA) Transaction Manager")
            TransactionManagerServices.transactionManager.shutdown()
        }
    }

    private static void createUserAndMoveUsers(final EntityManagerFactory emf1, final EntityManagerFactory emf2) {
        UserTransaction tx = TransactionManagerServices.getTransactionManager()
        try {
            log.debug("Starting Transaction")
            tx.begin()
            log.info("Persisting one new User in users1")
            createUser(emf1)
            log.debug("Commiting Transaction")
            tx.commit()
        } catch (final Exception e) {
            log.error("Failed to add user to users1", e)
            tx.rollback()
            throw e
        }

        try {
            log.debug("Starting Transaction")
            tx.begin()
            log.info("Moving users from users1 to users2")
            moveUsers(emf1, emf2)
            log.debug("Commiting Transaction")
            tx.commit()
        } catch (final Exception e) {
            log.error("Failed to move users; none was moved", e)
            tx.rollback()
            throw e
        }
    }

    private static void createUser(final EntityManagerFactory emf) {
        log.trace("Obtaining (JPA) Entity Manager")
        EntityManager em = emf.createEntityManager()

        try {
            User user = new User(name: "Test User")
            em.persist(user)
        } finally {
            log.trace("Closing (JPA) Entity Manager")
            em.close()
        }
    }

    private static void moveUsers(final EntityManagerFactory emf1, final EntityManagerFactory emf2) {
        log.trace("Obtaining (JPA) Entity Managers")
        EntityManager em1 = emf1.createEntityManager()
        EntityManager em2 = emf2.createEntityManager()

        log.info(" - Querying for users in users1")
        List<User> users1 = (List<User>) em1.createQuery("select u from fi.linuxbox.jta.User u").getResultList()

        try {
            log.info(" - Moving those users to users2")
            for (User user1 : users1) {
                User user2 = new User(name: user1.getName())
                log.info("   - moving a user")
                em2.persist(user2)
                em1.remove(user1)
            }
        } finally {
            log.trace("Closing (JPA) Entity Managers")
            em1.close()
            em2.close()
        }
    }

    /**
     * @param databaseName
     *      The Derby database name (the directory name).
     * @param jndiName
     *      Hibernate will find the data source in JNDI by this name.
     * @return
     */
    private static void initDerbyDataSource(final String databaseName, final String jndiName, final boolean allowLocalTransactions) {
        final PoolingDataSource ds = new PoolingDataSource()
        ds.allowLocalTransactions = allowLocalTransactions
        ds.className = "org.apache.derby.jdbc.EmbeddedXADataSource"
        ds.maxPoolSize = 3
        ds.uniqueName = jndiName
        ds.driverProperties["databaseName"] = databaseName
        log.trace("Initializing $databaseName pooled data source")
        ds.init()
    }
}