package database.dao;

import com.mongodb.DB;
import com.mongodb.MongoException;
import com.mongodb.WriteResult;
import com.mongodb.client.ClientSession;
import database.DALException;
import database.DataSource;
import database.NoModificationException;
import database.collections.Event;
import database.collections.Message;
import database.collections.Playground;
import database.collections.User;
import database.utils.QueryUtils;
import org.bson.types.ObjectId;
import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.jongo.MongoCursor;
import org.mindrot.jbcrypt.BCrypt;

import java.util.*;

public class Controller implements IController {
    private static IController beta;
    private IPlaygroundDAO playgroundDAO;
    private IUserDAO userDAO;
    private IMessageDAO messageDAO;
    private IEventDAO eventDAO;

    private Controller(DB db) {
        this.playgroundDAO = new PlaygroundDAO(db);
        this.userDAO = new UserDAO(db);
        this.messageDAO = new MessageDAO(db);
        this.eventDAO = new EventDAO(db);
    }

    public static IController getInstance(DB testDB) {
        if (beta == null) {
            beta = new Controller(testDB);
        }
        return beta;
    }

    @Override
    public WriteResult createPlayground(Playground playground) throws NoModificationException {
        return playgroundDAO.createPlayground(playground);
    }

    @Override
    public WriteResult createUser(User user) throws NoModificationException {
        String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        user.setPassword(hashedPassword);
        return userDAO.createUser(user);
    }

    @Override
    public Playground getPlayground(String playgroundName) throws NoModificationException {
        Playground playground = playgroundDAO.getPlayground(playgroundName);

        // fetch assigned pedagogues based on username
        Set<User> assignedPedagogue = playground.getAssignedPedagogue();
        Set<User> updatedPedagogue = new HashSet<>();
        if (!assignedPedagogue.isEmpty()) {
            for (User usernameObj : assignedPedagogue) {
                User user = userDAO.getUser(usernameObj.getUsername());
                updatedPedagogue.add(user);
            }
        }
        playground.setAssignedPedagogue(updatedPedagogue);

        // fetch events based on id
        Set<Event> events = playground.getEvents();
        Set<Event> updatedEvents = new HashSet<>();
        if (!events.isEmpty()) {
            for (Event idObj : events) {
                Event event = eventDAO.getEvent(idObj.getId());
                updatedEvents.add(event);
            }
        }
        playground.setEvents(updatedEvents);


        // fetch messages based on id
        Set<Message> messages = playground.getMessages();
        Set<Message> updatedMessage = new HashSet<>();
        if (!messages.isEmpty()) {
            for (Message idObj : messages) {
                Message message = messageDAO.getMessage(idObj.getId());
                updatedMessage.add(message);
            }
        }
        playground.setMessages(updatedMessage);
        return playground;
    }

    @Override
    public User getUser(String username) throws DALException {
        User user = userDAO.getUser(username);

        // fetch all events based on id
        Set<Event> events = user.getEvents();
        Set<Event> updatedEvents = new HashSet<>();
        if (!events.isEmpty()) {
            for (Event value : events) {
                Event event = eventDAO.getEvent(value.getId());
                updatedEvents.add(event);
            }
        }
        user.setEvents(updatedEvents);
        return user;
    }

    @Override
    public Event getEvent(String eventID) {
        Event event = null;
        event = eventDAO.getEvent(eventID);

        // fetch all users based on id
        Set<User> users = event.getAssignedUsers();
        Set<User> updatedUser = new HashSet<>();
        if (!users.isEmpty()) {
            for (User user : users) {
                User u = userDAO.getUser(user.getUsername());
                updatedUser.add(u);
            }
        }
        event.setAssignedUsers(updatedUser);


        return event;
    }

    @Override
    public Message getMessage(String messageID) throws NoModificationException {
        return messageDAO.getMessage(messageID);
    }

    @Override
    public List<Playground> getPlaygrounds() {
        return playgroundDAO.getPlaygroundList();
    }

    @Override
    public List<User> getUsers() {
        return userDAO.getUserList();
    }

    @Override
    public List<Event> getPlaygroundEvents(String playgroundName) {
        Jongo jongo = new Jongo(DataSource.getProductionDB());
        MongoCollection events = jongo.getCollection(IEventDAO.COLLECTION);
        MongoCursor<Event> cursor = events.find("{playground : #}", playgroundName).as(Event.class);
        List<Event> eventList = new ArrayList<>();
        for (Event event : cursor)
            eventList.add(event);

        return eventList;
    }

    @Override
    public List<Message> getPlaygroundMessages(String playgroundName) {
        Jongo jongo = new Jongo(DataSource.getProductionDB());
        MongoCollection messages = jongo.getCollection(IMessageDAO.COLLECTION);
        MongoCursor<Message> cursor = messages.find("{playgroundID : #}", playgroundName).as(Message.class);
        List<Message> messageList = new ArrayList<>();
        for (Message message : cursor)
            messageList.add(message);

        return messageList;
    }

    @Override
    public WriteResult updatePlayground(Playground playground) throws NoModificationException {
        return playgroundDAO.updatePlayground(playground);
    }

    @Override
    public WriteResult updateUser(User user) throws NoModificationException {
        return userDAO.updateUser(user);
    }

    @Override
    public WriteResult updatePlaygroundEvent(Event event) throws NoModificationException {
        return eventDAO.updateEvent(event);
    }

    @Override
    public WriteResult updatePlaygroundMessage(Message message) throws NoModificationException {
        return messageDAO.updateMessage(message);
    }

    @Override
    public WriteResult deletePlayground(String playgroundName) throws DALException, NoModificationException {
        WriteResult isPlaygroundDeleted = null;

        final ClientSession session = DataSource.getProductionClient().startSession();
        try (session){
            session.startTransaction();

            Playground playground = playgroundDAO.getPlayground(playgroundName);

            // delete playground reference from pedagogues
            MongoCollection usersCollection = new Jongo(DataSource.getProductionDB()).getCollection(IUserDAO.COLLECTION);
            for (User pedagogue : playground.getAssignedPedagogue())
                QueryUtils.updateWithPullSimple(usersCollection, "username", pedagogue.getUsername(), "playgroundsIDs", playgroundName);

            // delete playground events
            for (Event event : playground.getEvents())
                removePlaygroundEvent(event.getId());

            // delete playground messages
            for (Message message : playground.getMessages())
                removePlaygroundMessage(message.getId());

            // delete playground
            isPlaygroundDeleted = playgroundDAO.deletePlayground(playgroundName);

            session.commitTransaction();
        } catch (MongoException e){
            session.abortTransaction();
        }

        return isPlaygroundDeleted;
    }

    @Override
    public WriteResult deleteUser(String username) {
        //  final ClientSession clientSession = DataSource.getClient().startSession();

        WriteResult writeResult = null;
        //clientSession.startTransaction();
        try {
            User user = userDAO.getUser(username);

            // delete user reference from playground
            for (String playgroundName : user.getPlaygroundsIDs())
                removePedagogueFromPlayground(playgroundName, username);

            // delete user reference in events
            for (Event event : user.getEvents())
                removeUserFromPlaygroundEvent(event.getId(), username);

            // delete user
            writeResult = userDAO.deleteUser(username);

            //  clientSession.commitTransaction();
        } catch (Exception e) {
            // clientSession.abortTransaction();
            e.printStackTrace();
        } finally {
            //clientSession.close();
        }

        return writeResult;
    }

    @Override
    public boolean addPedagogueToPlayground(String plagroundName, String username) {
//        final ClientSession clientSession = DataSource.getClient().startSession();
        //  clientSession.startTransaction();
        try {
            // insert playground reference in user
            User pedagogue = userDAO.getUser(username);
            pedagogue.getPlaygroundsIDs().add(plagroundName);
            userDAO.updateUser(pedagogue);

            // insert user reference in playground
            MongoCollection playgrounds = new Jongo(DataSource.getProductionDB()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPush(playgrounds, "name", plagroundName, "assignedPedagogue", pedagogue);

            //        clientSession.commitTransaction();
        } catch (Exception e) {
            //      clientSession.abortTransaction();
            e.printStackTrace();
        } finally {
            //    clientSession.close();
        }

        return true;
    }

    public boolean addPedagogueToPlayground(User user) {
//        final ClientSession clientSession = DataSource.getClient().startSession();
        //  clientSession.startTransaction();
        try {

            for (String playgroundName : user.getPlaygroundsIDs()) {
                Playground playground = Controller.getInstance(DataSource.getTestDB()).getPlayground(playgroundName);
                playground.getAssignedPedagogue().add(user);
                Controller.getInstance(DataSource.getTestDB()).updatePlayground(playground);
            }


            //        clientSession.commitTransaction();
        } catch (Exception e) {
            //      clientSession.abortTransaction();
            e.printStackTrace();
        } finally {
            //    clientSession.close();
        }

        return true;
    }

    // to do fix it
    @Override
    public boolean addUserToPlaygroundEvent(String eventID, String username) {
//        ClientSession clientSession = DataSource.getClient().startSession();
        //       clientSession.startTransaction();
        try {
            // update user with event reference
            User user = userDAO.getUser(username);
            user.getEvents().add(new Event.Builder().id(new ObjectId(eventID).toString()).build());
            userDAO.updateUser(user);

            // insert user reference in event
            Jongo jongo = new Jongo(DataSource.getProductionDB());
            MongoCollection events = jongo.getCollection(IEventDAO.COLLECTION);
            QueryUtils.updateWithPush(events, "_id", new ObjectId(eventID), "assignedUsers", user);

            //    clientSession.commitTransaction();
        } catch (Exception e) {
            //    clientSession.abortTransaction();
            e.printStackTrace();
        } finally {
            //clientSession.close();
        }

        return true;
    }

    @Override
    public WriteResult addPlaygroundEvent(String playgroundName, Event event) {
        //ClientSession clientSession = DataSource.getClient().startSession();
        WriteResult result = null;
        //clientSession.startTransaction();
        try {
            // create event in event collection
            event.setPlayground(playgroundName);
            result = eventDAO.createEvent(event);

            // insert event id in playground
            MongoCollection playgrounds = new Jongo(DataSource.getProductionDB()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPush(playgrounds, "name", playgroundName, "events", event);

            //    clientSession.commitTransaction();
        } catch (Exception e) {
            //  clientSession.abortTransaction();
            e.printStackTrace();
        } finally {
            //  clientSession.close();
        }

        return result;
    }

    @Override
    public WriteResult addPlaygroundMessage(String playgroundName, Message message) {
        //ClientSession clientSession = DataSource.getClient().startSession();
        WriteResult result = null;
        //clientSession.startTransaction();
        try {
            // create message in message collection
            message.setPlaygroundID(playgroundName);
            result = messageDAO.createMessage(message);

            // update playground array with reference to message
            MongoCollection playgrounds = new Jongo(DataSource.getProductionDB()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPush(playgrounds, "name", playgroundName, "messages", message);

            //   clientSession.commitTransaction();
        } catch (Exception e) {
            //  clientSession.abortTransaction();
            e.printStackTrace();
        } finally {
            //  clientSession.close();
        }

        return result;
    }

    @Override
    public boolean removePedagogueFromPlayground(String playgroundName, String username) throws NoModificationException {
       /* Kan ikke få det til at virke
       MongoCollection playground = new Jongo(DataSource.getDB()).getCollection(IPlaygroundDAO.COLLECTION);
        try {
            QueryUtils.updateWithPullObject(playground, "name", playgroundName, "assignedPedagogue", "username", username);
        } catch (DALException e) {
            e.printStackTrace();
        }
        return true;*/
        User removeUser = null;
        Playground playground = Controller.getInstance(DataSource.getTestDB()).getPlayground(playgroundName);
        for (User user : playground.getAssignedPedagogue()) {
            if (user.getUsername().equalsIgnoreCase(username)) {
                removeUser = user;
                break;
            }
        }
        playground.getAssignedPedagogue().remove(removeUser);
        Controller.getInstance(DataSource.getTestDB()).updatePlayground(playground);
        return true;
    }

    @Override
    public boolean removeUserFromPlaygroundEvent(String eventID, String username) {
        try {
            // delete user reference in event
            MongoCollection events = new Jongo(DataSource.getProductionDB()).getCollection(IEventDAO.COLLECTION);
            QueryUtils.updateWithPullObject(events, "_id", new ObjectId(eventID), "assignedUsers", "username", username);

            // delete event reference in user
            MongoCollection users = new Jongo(DataSource.getProductionDB()).getCollection(IUserDAO.COLLECTION);
            QueryUtils.updateWithPullObject(users, "username", username, "events", "_id", new ObjectId(eventID));
        } catch (DALException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public WriteResult removePlaygroundEvent(String eventID) {
        //ClientSession clientSession = DataSource.getClient().startSession();
        WriteResult isEventDeleted = null;
        //clientSession.startTransaction();
        try {
            Event event = eventDAO.getEvent(eventID);

            // delete event reference in users
            MongoCollection users = new Jongo(DataSource.getProductionDB()).getCollection(IUserDAO.COLLECTION);
            for (User user : event.getAssignedUsers()) {
                QueryUtils.updateWithPullObject(users, "username", user.getUsername(), "events", "_id", new ObjectId(eventID));
            }

            // delete event reference in playground
            MongoCollection playgrounds = new Jongo(DataSource.getProductionDB()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPullObject(playgrounds, "name", event.getPlaygroundName(), "events", "_id", new ObjectId(eventID));

            // delete event
            isEventDeleted = eventDAO.deleteEvent(eventID);

            //clientSession.commitTransaction();
        } catch (Exception e) {
            //clientSession.abortTransaction();
            e.printStackTrace();
        } finally {
            // clientSession.close();
        }

        return isEventDeleted;
    }

    @Override
    public WriteResult removePlaygroundMessage(String messageID) {
        //ClientSession clientSession = DataSource.getClient().startSession();
        WriteResult isMessageDeleted = null;
        //clientSession.startTransaction();
        try {
            Message message = messageDAO.getMessage(messageID);

            // delete message reference in playground
            MongoCollection playground = new Jongo(DataSource.getProductionDB()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPullObject(playground, "name", message.getPlaygroundName(), "messages", "_id", new ObjectId(messageID));

            // delete message
            isMessageDeleted = messageDAO.deleteMessage(messageID);

            //clientSession.commitTransaction();
        } catch (Exception e) {
            //clientSession.abortTransaction();
            e.printStackTrace();
        } finally {
            //clientSession.close();
        }

        return isMessageDeleted;
    }
}
