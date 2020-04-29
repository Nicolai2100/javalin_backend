package database.dao;

import com.mongodb.MongoException;
import com.mongodb.WriteResult;
import com.mongodb.client.ClientSession;
import database.IDataSource;
import database.exceptions.NoModificationException;
import database.ProductionDB;
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
    private static IController controller;
    private IDataSource datasource;
    private IPlaygroundDAO playgroundDAO;
    private IUserDAO userDAO;
    private IMessageDAO messageDAO;
    private IEventDAO eventDAO;

    private Controller() {
        this.datasource = ProductionDB.getInstance(); // production by default
        this.playgroundDAO = new PlaygroundDAO(datasource);
        this.userDAO = new UserDAO(datasource);
        this.messageDAO = new MessageDAO(datasource);
        this.eventDAO = new EventDAO(datasource);
    }

    public static IController getInstance() {
        if (controller == null) {
            controller = new Controller();
        }
        return controller;
    }

    @Override
    public WriteResult createPlayground(Playground playground) throws IllegalArgumentException, NoModificationException {
        return playgroundDAO.createPlayground(playground);
    }

    @Override
    public WriteResult createUser(User user) throws IllegalArgumentException, NoModificationException {
        String hashedPassword = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());
        user.setPassword(hashedPassword);
        return userDAO.createUser(user);
    }

    @Override
    public Playground getPlayground(String playgroundName) throws IllegalArgumentException, NoSuchElementException {
        Playground playground = playgroundDAO.getPlayground(playgroundName);

        // fetch assigned pedagogues based on username
        Set<User> updatedPedagogue = new HashSet<>();
        Set<User> assignedPedagogue = playground.getAssignedPedagogue();
        if (!assignedPedagogue.isEmpty()) {
            for (User usernameObj : assignedPedagogue) {
                User user = userDAO.getUser(usernameObj.getUsername());
                updatedPedagogue.add(user);
            }
        }
        playground.setAssignedPedagogue(updatedPedagogue);

        // fetch events based on id
        Set<Event> updatedEvents = new HashSet<>();
        Set<Event> events = playground.getEvents();
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
    public User getUser(String username) throws NoSuchElementException, IllegalArgumentException {
        User user = userDAO.getUser(username);

        // fetch all events based on id
        Set<Event> updatedEvents = new HashSet<>();
        Set<Event> events = user.getEvents();
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
    public Event getEvent(String eventID) throws IllegalArgumentException, NoSuchElementException {
        Event event = eventDAO.getEvent(eventID);

        // fetch all users based on id
        Set<User> updatedUser = new HashSet<>();
        Set<User> users = event.getAssignedUsers();
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
    public Message getMessage(String messageID) throws IllegalArgumentException, NoSuchElementException {
        return messageDAO.getMessage(messageID);
    }

    @Override
    public List<Playground> getPlaygrounds() throws NoSuchElementException {
        return playgroundDAO.getPlaygroundList();
    }

    @Override
    public List<User> getUsers() throws NoSuchElementException {
        return userDAO.getUserList();
    }

    @Override
    public List<Event> getEventsInPlayground(String playgroundName) {
        Jongo jongo = new Jongo(datasource.getDatabase());
        MongoCollection events = jongo.getCollection(IEventDAO.COLLECTION);
        MongoCursor<Event> cursor = events.find("{playground : #}", playgroundName).as(Event.class);
        List<Event> eventList = new ArrayList<>();
        for (Event event : cursor)
            eventList.add(event);

        return eventList;
    }

    @Override
    public List<Message> getMessagesInPlayground(String playgroundName) {
        Jongo jongo = new Jongo(datasource.getDatabase());
        MongoCollection messages = jongo.getCollection(IMessageDAO.COLLECTION);
        MongoCursor<Message> cursor = messages.find("{playgroundID : #}", playgroundName).as(Message.class);
        List<Message> messageList = new ArrayList<>();
        for (Message message : cursor)
            messageList.add(message);

        return messageList;
    }

    @Override
    public WriteResult updatePlayground(Playground playground)
            throws IllegalArgumentException, NoModificationException {
        return playgroundDAO.updatePlayground(playground);
    }

    @Override
    public WriteResult updateUser(User user) throws IllegalArgumentException, NoModificationException {
        return userDAO.updateUser(user);
    }

    @Override
    public WriteResult updatePlaygroundEvent(Event event)
            throws IllegalArgumentException, NoModificationException {
        return eventDAO.updateEvent(event);
    }

    @Override
    public WriteResult updatePlaygroundMessage(Message message)
            throws IllegalArgumentException, NoModificationException {
        return messageDAO.updateMessage(message);
    }

    @Override
    public WriteResult deletePlayground(String playgroundName)
            throws NoSuchElementException, NoModificationException, MongoException {

        WriteResult wr;
        final ClientSession session = datasource.getClient().startSession();
        try (session){
            session.startTransaction();
            Playground playground = playgroundDAO.getPlayground(playgroundName);

            // delete playground reference from pedagogues
            for (User pedagogue : playground.getAssignedPedagogue())
                removePedagogueFromPlayground(playgroundName, pedagogue.getUsername());

            // delete playground events
            for (Event event : playground.getEvents())
                deletePlaygroundEvent(event.getId());

            // delete playground messages
            for (Message message : playground.getMessages())
                deletePlaygroundMessage(message.getId());

            // delete playground
            wr = playgroundDAO.deletePlayground(playgroundName);
            session.commitTransaction();

        } catch (NoSuchElementException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

        return wr;
    }

    @Override
    public WriteResult deleteUser(String username)
            throws NoModificationException, NoSuchElementException, MongoException {

        final ClientSession session = datasource.getClient().startSession();
        WriteResult wr;
        try (session){
            session.startTransaction();
            User user = userDAO.getUser(username);

            // delete user reference from playground
            for (String playgroundName : user.getPlaygroundsIDs())
                removePedagogueFromPlayground(playgroundName, username);

            // delete user reference in events
            for (Event event : user.getEvents())
                removeUserFromEvent(event.getId(), username);

            // delete user
            wr = userDAO.deleteUser(username);
            session.commitTransaction();

        } catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

        return wr;
    }

    @Override
    public WriteResult addPedagogueToPlayground(String plagroundName, String username)
            throws NoModificationException, NoSuchElementException, MongoException {

        final ClientSession session = datasource.getClient().startSession();
        WriteResult wr;
        try(session) {
            session.startTransaction();
            User pedagogue = userDAO.getUser(username);

            // insert playground reference in user
            pedagogue.getPlaygroundsIDs().add(plagroundName);
            wr = userDAO.updateUser(pedagogue);

            // insert user reference in playground
            MongoCollection playgrounds = new Jongo(datasource.getDatabase()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPush(playgrounds, "name",
                    plagroundName, "assignedPedagogue", pedagogue);

            session.commitTransaction();
        } catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

        return wr;
    }


    /* //TODO: what is this?
    public boolean addPedagogueToPlayground(User user) {
        final ClientSession session = DataSource.getProductionClient().startSession();
        WriteResult wr;
        try (session) {
            session.startTransaction();

            for (String playgroundName : user.getPlaygroundsIDs()) {
                Playground playground = Controller.getInstance(DataSource.getTestDB()).getPlayground(playgroundName);
                playground.getAssignedPedagogue().add(user);
                Controller.getInstance(DataSource.getTestDB()).updatePlayground(playground);
            }


            session.commitTransaction();
        } catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

        return wr;
    }
     */
    @Override
    public WriteResult addUserToEvent(String eventID, String username)
            throws NoModificationException, NoSuchElementException, MongoException {

        ClientSession session = datasource.getClient().startSession();
        WriteResult wr;

        try(session) {
            session.startTransaction();
            User user = userDAO.getUser(username);

            // update user with event reference
            user.getEvents().add(new Event.Builder().id(new ObjectId(eventID).toString()).build());
            wr = userDAO.updateUser(user);

            // insert user reference in event
            Jongo jongo = new Jongo(datasource.getDatabase());
            MongoCollection events = jongo.getCollection(IEventDAO.COLLECTION);
            QueryUtils.updateWithPush(events, "_id", new ObjectId(eventID), "assignedUsers", user);

            session.commitTransaction();
        } catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

        return wr;
    }

    @Override
    public WriteResult createPlaygroundEvent(String playgroundName, Event event)
            throws NoModificationException, NoSuchElementException, MongoException {

        ClientSession session = datasource.getClient().startSession();
        WriteResult wr;

        try (session) {
            session.startTransaction();

            // create event in event collection
            event.setPlayground(playgroundName);
            wr = eventDAO.createEvent(event);

            // insert event id in playground
            MongoCollection playgrounds = new Jongo(datasource.getDatabase()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPush(playgrounds, "name", playgroundName, "events", event);

            session.commitTransaction();
        } catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

        return wr;
    }

    @Override
    public WriteResult createPlaygroundMessage(String playgroundName, Message message)
            throws NoModificationException, NoSuchElementException, MongoException {

        ClientSession session = datasource.getClient().startSession();
        WriteResult result;

        try (session) {
            session.startTransaction();

            // create message in message collection
            message.setPlaygroundID(playgroundName);
            result = messageDAO.createMessage(message);

            // update playground array with reference to message
            MongoCollection playgrounds = new Jongo(datasource.getDatabase()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPush(playgrounds, "name", playgroundName, "messages", message);

            session.commitTransaction();
        } catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

        return result;
    }

    @Override
    public void removePedagogueFromPlayground(String playgroundName, String username)
            throws NoModificationException, NoSuchElementException, MongoException {

        ClientSession session = datasource.getClient().startSession();
        try(session){
            session.startTransaction();

            // remove user reference in playground
            MongoCollection playground = new Jongo(datasource.getDatabase()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPullObject(playground, "name", playgroundName, "assignedPedagogue", "username", username);

            // remove playground reference in user
            MongoCollection user = new Jongo(datasource.getDatabase()).getCollection(IUserDAO.COLLECTION);
            QueryUtils.updateWithPullSimple(user, "username", username, "playgroundsIDs", playgroundName);
            session.commitTransaction();
        } catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

       /*
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

        */
    }

    @Override
    public WriteResult deletePlaygroundMessage(String messageID)
            throws NoModificationException, NoSuchElementException, MongoException {

        ClientSession session = datasource.getClient().startSession();
        WriteResult wr;
        try (session){
            session.startTransaction();
            Message message = messageDAO.getMessage(messageID);

            // delete message reference in playground
            MongoCollection playground = new Jongo(datasource.getDatabase()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPullObject(playground, "name", message.getPlaygroundName(), "messages", "_id", new ObjectId(messageID));

            // delete message
            wr = messageDAO.deleteMessage(messageID);
            session.commitTransaction();
        } catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

        return wr;
    }

    @Override
    public void removeUserFromEvent(String eventID, String username)
            throws NoModificationException, NoSuchElementException, MongoException {

        ClientSession session = datasource.getClient().startSession();
        try (session){
            session.startTransaction();

            // delete user reference in event
            MongoCollection events = new Jongo(datasource.getDatabase()).getCollection(IEventDAO.COLLECTION);
            QueryUtils.updateWithPullObject(events, "_id", new ObjectId(eventID), "assignedUsers", "username", username);

            // delete event reference in user
            MongoCollection users = new Jongo(datasource.getDatabase()).getCollection(IUserDAO.COLLECTION);
            QueryUtils.updateWithPullObject(users, "username", username, "events", "_id", new ObjectId(eventID));
            session.commitTransaction();

        }catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }
    }

    @Override
    public WriteResult deletePlaygroundEvent(String eventID)
            throws NoModificationException, NoSuchElementException, MongoException {

        ClientSession session = datasource.getClient().startSession();
        WriteResult wr;

        try (session) {
            session.startTransaction();
            Event event = eventDAO.getEvent(eventID);

            // delete event reference in users
            MongoCollection users = new Jongo(datasource.getDatabase()).getCollection(IUserDAO.COLLECTION);
            for (User user : event.getAssignedUsers()) {
                QueryUtils.updateWithPullObject(users, "username", user.getUsername(), "events", "_id", new ObjectId(eventID));
            }

            // delete event reference in playground
            MongoCollection playgrounds = new Jongo(datasource.getDatabase()).getCollection(IPlaygroundDAO.COLLECTION);
            QueryUtils.updateWithPullObject(playgrounds, "name", event.getPlaygroundName(), "events", "_id", new ObjectId(eventID));

            // delete event
            wr = eventDAO.deleteEvent(eventID);
            session.commitTransaction();

        } catch (NoSuchElementException e) {
            session.abortTransaction();
            e.printStackTrace();
            throw new NoSuchElementException(e.getMessage());
        } catch (NoModificationException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new NoModificationException(e.getMessage());
        } catch (MongoException e){
            session.abortTransaction();
            e.printStackTrace();
            throw new MongoException("Internal error");
        }

        return wr;
    }

    @Override
    public void killAll(){
        playgroundDAO.deleteAllPlaygrounds();
        userDAO.deleteAllUsers();
        messageDAO.deleteAllMessages();
        eventDAO.deleteAllEvents();
    }

    @Override
    public void setDataSource(IDataSource dataSource) {
        this.datasource = dataSource;
        playgroundDAO.setDataSource(dataSource);
        userDAO.setDataSource(dataSource);
        messageDAO.setDataSource(dataSource);
        eventDAO.setDataSource(dataSource);
    }
}
