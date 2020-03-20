package Database.collections;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    public void insertEventAndRetrieveEvent() throws SystemException, NotSupportedException, HeuristicRollbackException, HeuristicMixedException, RollbackException {
        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("ogm-mongodb");
        TransactionManager transactionManager = com.arjuna.ats.jta.TransactionManager.transactionManager();
        EntityManager entityManager = entityManagerFactory.createEntityManager();

        transactionManager.begin();

        Details details1 = new Details(new Date(System.currentTimeMillis()), new Date(System.currentTimeMillis()), new Date(System.currentTimeMillis()));
        Details details2 = new Details(new Date(System.currentTimeMillis()), new Date(System.currentTimeMillis()), new Date(System.currentTimeMillis()));
        Event event1 = new Event("Football", "2k3j2k323.jpg", 20, "Football near the bay...", details1);
        Event event2 = new Event("Boardgames", "a9sd8sa9d89as8d.jpg", 3, "Boardgames near the bay...", details2);

        List<PhoneNumber> numbers1 = new ArrayList<>();
        numbers1.add(new PhoneNumber("+45 23 12 23 45"));
        numbers1.add(new PhoneNumber("+45 23 45 12 32"));
        User user1 = new User("Nicolai", "Admin", "qwe123", "nicolai.nisbeth@yahoo.com", "asdasd97asdasd.jpg", numbers1);

        List<PhoneNumber> numbers2 = new ArrayList<>();
        numbers2.add(new PhoneNumber("+45 89 76 78 34"));
        numbers2.add(new PhoneNumber("+45 75 23 12 32"));
        User user2 = new User("Peter", "Admin", "gkgkdl123", "awqds.pavlidi@google.com", "as23dasd97asd23asd.jpg", numbers2);

        event1.getAssignedUsers().add(user1);
        event1.getAssignedUsers().add(user2);
        user1.getEvents().add(event1);
        user2.getEvents().add(event1);

        event2.getAssignedUsers().add(user1);
        user1.getEvents().add(event2);



        entityManager.persist(event1);
        entityManager.persist(event2);

        transactionManager.commit();

        // get a new EM to make sure data is actually retrieved from the store and not Hibernate's internal cache
        entityManager.close();
        entityManager = entityManagerFactory.createEntityManager();

        // load it back
        entityManager.getTransaction().begin();

        Event loadedEvent = entityManager.find(Event.class, event1.getId());
        Event loadedEvent2 = entityManager.find(Event.class, event2.getId());

        User loadedUser = entityManager.find(User.class, user1.getId());
        User loadedUser2 = entityManager.find(User.class, user2.getId());



        entityManager.getTransaction().commit();
        entityManager.close();


    }
}