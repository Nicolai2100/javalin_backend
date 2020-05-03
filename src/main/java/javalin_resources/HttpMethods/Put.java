package javalin_resources.HttpMethods;

import com.google.gson.JsonArray;
import database.DALException;
import database.collections.*;
import database.dao.Controller;
import io.javalin.http.Handler;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import javax.mail.MessagingException;
import java.awt.image.BufferedImage;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class Put implements Tag {

    public static class PutEvent {

        public static Handler updateEventToPlaygroundPut = ctx -> {
            JSONObject jsonObject = new JSONObject(ctx.body());
            Event event = Controller.getInstance().getEvent(ctx.pathParam(EVENT_ID));
            Playground playground = Controller.getInstance().getPlayground(ctx.pathParam(PLAYGROUND_NAME));

            if (playground != null) {

                if (jsonObject.has(EVENT_ID)) {
                    event.setId(jsonObject.getString(EVENT_ID));
                }
                if (jsonObject.has(EVENT_NAME)) {
                    event.setName(jsonObject.getString(EVENT_NAME));
                }
                if (jsonObject.has(EVENT_IMAGEPATH)) {
                    event.setImagepath(jsonObject.getString(EVENT_IMAGEPATH));
                }
                if (jsonObject.has(EVENT_PARTICIPANTS)) {
                    event.setParticipants(jsonObject.getInt(EVENT_PARTICIPANTS));
                }
                if (jsonObject.has(EVENT_DESCRIPTION)) {
                    event.setDescription(jsonObject.getString(EVENT_DESCRIPTION));
                }
                if (jsonObject.has(EVENT_DETAILS)) {
                    //TODO: Change this
                    event.setDetails(null);
                }
                if (jsonObject.has(EVENT_ASSIGNED_USERS)) {
                    Set<User> assignedUsers = new HashSet<>();
                    for (int i = 0; i < jsonObject.getJSONArray(EVENT_ASSIGNED_USERS).length(); i++) {
                        String assignedUserId = jsonObject.getJSONArray(EVENT_ASSIGNED_USERS).getJSONObject(i).getString(EVENT_ASSIGNED_USERS);
                        assignedUsers.add(Controller.getInstance().getUser(assignedUserId));
                    }
                    event.setAssignedUsers(assignedUsers);
                }
                if (jsonObject.has(PLAYGROUND_NAME)) {
                    event.setPlayground(PLAYGROUND_NAME);
                }

                if (Controller.getInstance().updatePlaygroundEvent(event)) {
                    ctx.status(200).result("Event is updated");
                }
            } else {
                ctx.status(404).result("Couldn't update event");
            }
        };

    }

    public static class PutPlayground {

        public static Handler updatePlaygroundPut = ctx -> {
            Playground playground = Controller.getInstance().getPlayground(ctx.pathParam(PLAYGROUND_NAME));
            JSONObject jsonObject = new JSONObject(ctx.body());
            if (playground != null) {
                if (jsonObject.has(PLAYGROUND_STREET_NAME))
                    playground.setStreetName(jsonObject.getString(PLAYGROUND_STREET_NAME));

                if (jsonObject.has(PLAYGROUND_PEDAGOGUES)) {
                    Set<User> pedagoges = new HashSet<>();

                    for (int i = 0; i < jsonObject.getJSONArray(PLAYGROUND_PEDAGOGUES).length(); i++) {
                        String username = jsonObject.getJSONArray(PLAYGROUND_PEDAGOGUES).getString(i);
                        pedagoges.add(Controller.getInstance().getUser(username));
                    }
                    playground.setAssignedPedagogue(pedagoges);
                }

                if (jsonObject.has(PLAYGROUND_COMMUNE))
                    playground.setCommune(jsonObject.getString(PLAYGROUND_COMMUNE));

                if (jsonObject.has(PLAYGROUND_EVENTS)) {
                    Set<Event> eventSet = new HashSet<>();
                    for (int i = 0; i < jsonObject.getJSONArray(PLAYGROUND_EVENTS).length(); i++) {
                        String eventid = jsonObject.getJSONArray(PLAYGROUND_EVENTS).getJSONObject(i).getString(PLAYGROUND_EVENTS);
                        eventSet.add(Controller.getInstance().getEvent(eventid));
                    }
                    playground.setEvents(eventSet);
                }
                if (jsonObject.has(PLAYGROUND_HASSOCCERFIELD))
                    playground.setHasSoccerField(jsonObject.getBoolean(PLAYGROUND_HASSOCCERFIELD));

                if (jsonObject.has(PLAYGROUND_ID))
                    playground.setId(jsonObject.getString(PLAYGROUND_ID));

                if (jsonObject.has(PLAYGROUND_IMAGEPATH))
                    playground.setImagePath(jsonObject.getString(PLAYGROUND_IMAGEPATH));

                if (jsonObject.has(PLAYGROUND_MESSAGES)) {
                    Set<Message> messagesSet = new HashSet<>();
                    for (int i = 0; i < jsonObject.getJSONArray(PLAYGROUND_MESSAGE_ID).length(); i++) {
                        String messageid = jsonObject.getJSONArray(PLAYGROUND_MESSAGE_ID).getJSONObject(i).getString(PLAYGROUND_MESSAGE_ID);
                        messagesSet.add(Controller.getInstance().getMessage(messageid));
                    }
                    playground.setMessages(messagesSet);
                }

                if (jsonObject.has(PLAYGROUND_STREET_NUMBER))
                    playground.setStreetNumber(jsonObject.getInt(PLAYGROUND_STREET_NUMBER));

                if (jsonObject.has(PLAYGROUND_TOILETS))
                    playground.setToiletPossibilities(jsonObject.getBoolean(PLAYGROUND_TOILETS));

                if (jsonObject.has(PLAYGROUND_ZIPCODE))
                    playground.setZipCode(jsonObject.getInt(PLAYGROUND_ZIPCODE));

                if (Controller.getInstance().updatePlayground(playground)) {
                    ctx.status(200).result("Playground updated");
                    //Test
                    System.out.println("update playground with name " + playground.getName());
                }
            } else {
                ctx.status(404).result("Playground didn't update");
            }
        };

    }

    public static class PutPedagogue {

        public static Handler updatePedagogueToPlayGroundPut = ctx -> {
            JSONObject jsonObject = new JSONObject(ctx.body());
            Playground playground = Controller.getInstance().getPlayground(jsonObject.getString(PLAYGROUND_NAME));
            User user = Controller.getInstance().getUser(jsonObject.getString(PEDAGOGUE));
            playground.getAssignedPedagogue().add(user);
            Controller.getInstance().updatePlayground(playground);
            if (jsonObject.getString(PEDAGOGUE) != null && jsonObject.getString(PLAYGROUND_NAME) != null) {
                ctx.status(200).result("Updated Successfull");
            } else {
                ctx.status(404).result("Failed to update");
            }
        };

    }

    public static class PutUser {

        public static Handler resetPassword = ctx -> {
            JSONObject jsonObject = new JSONObject(ctx.body());
            String username = jsonObject.getString(USERNAME);
            User user = null;

            try {
                user = Controller.getInstance().getUser(username);
            } catch (DALException e) {
                ctx.status(401).result("Unauthorized");
                e.printStackTrace();
            }
            if (user.getEmail() == null) {
                //reset password
            } else {
                try {
                    String newPassword = "1234";
                    user.setPassword(newPassword);
                    Controller.getInstance().updateUser(user);
                    Controller.getInstance().getUser(user.getUsername());
                    SendMail.sendMail("Your new password", "Your new password is: " + newPassword, user.getEmail());
                } catch (MessagingException | DALException e) {
                    e.printStackTrace();
                }
            }

            ctx.status(401).result("Unauthorized - Wrong password");
        };

        public static Handler updateUser = ctx -> {
            BufferedImage bufferedImage;
            //String usermodel = ctx.formParam(("usermodel"));
            String usermodel = ctx.body();
            JSONObject jsonObject = new JSONObject(usermodel);

            String username = jsonObject.getString(USERNAME);
            String password = jsonObject.getString(PASSWORD);
            String firstName = jsonObject.getString(FIRSTNAME);
            String lastName = jsonObject.getString(LASTNAME);
            String email = jsonObject.getString(EMAIL);
            String status = jsonObject.getString(STATUS);
            JSONArray phoneNumber = jsonObject.getJSONArray(PHONENUMBER);
            String website = jsonObject.getString(WEBSITE);

            User admin = null;
            User userToUpdate = null;
            String usernameAdmin = null, passwordAdmin = null;
            try {
                JSONArray playgroundIDs = jsonObject.getJSONArray(PLAYGROUNDSIDS);
                usernameAdmin = jsonObject.getString(USERNAME_ADMIN);
                passwordAdmin = jsonObject.getString(PASSWORD_ADMIN);
                admin = Controller.getInstance().getUser(usernameAdmin);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (admin != null && !admin.getPassword().equalsIgnoreCase(passwordAdmin)) {
                System.out.println(admin.getPassword());
                System.out.println(passwordAdmin);
                ctx.status(401).result("Unauthorized - Kodeord er forkert...");
                Controller.getInstance().getUsers();
            } else {
                try {
                    userToUpdate = Controller.getInstance().getUser(username);
                } catch (DALException e) {
                    e.printStackTrace();
                }
                userToUpdate.setFirstname(firstName);
                userToUpdate.setLastname(lastName);
                userToUpdate.setStatus(status);
                userToUpdate.setEmail(email);
                userToUpdate.setWebsite(website);
                userToUpdate.setImagePath(String.format(IMAGEPATH + "/%s/profile-picture", username));
                String[] phoneNumbers = new String[1];
                phoneNumbers[0] = phoneNumber.getString(0);
                userToUpdate.setPhonenumbers(phoneNumbers);
                /*userToUpdate.getPlaygroundsIDs().removeAll(userToUpdate.getPlaygroundsIDs());
                for (Object id : playgroundIDs) {
                    userToUpdate.getPlaygroundsIDs().add(id.toString());
                }

                 */
                try {
                    bufferedImage = ImageIO.read(ctx.uploadedFile("image").getContent());
                    Shared.saveProfilePicture(username, bufferedImage);
                } catch (Exception e) {
                    System.out.println("Server: intet billede i upload");
                }
                System.out.println(userToUpdate);
                if (Controller.getInstance().updateUser(userToUpdate).wasAcknowledged()) {
                    ctx.status(201).result("User was updated");
                    ctx.json(userToUpdate);
                } else {
                    ctx.status(401).result("User was not updated");
                }
            }
            // TODO: hvorfor returneres alle users?
            //ctx.json(Controller.getInstance().getUsers());
        };
    }


    public static class PutMessage {

        public static Handler updatePlaygroundMessagePut = ctx -> {

            BufferedImage bufferedImage = null;
            String messageJson = ctx.formParam(("message"));;
            JSONObject jsonObject = new JSONObject(messageJson);
            Message message = Controller.getInstance().getMessage(jsonObject.getString("id"));

            // TODO Hvordan kommer den detail parameter til at foregå?
           /* if (jsonObject.get(HOUR) != null) {
                Calendar cal = Calendar.getInstance();

                cal.set(Calendar.YEAR, jsonObject.getInt(YEAR));
                cal.set(Calendar.DAY_OF_MONTH, jsonObject.getInt(DAY));
                cal.set(Calendar.MONTH, jsonObject.getInt(MONTH));


                cal.set(Calendar.HOUR, jsonObject.getInt(HOUR));
                cal.set(Calendar.MINUTE, jsonObject.getInt(MINUTE));
                message.setDate(cal.getTime());
            }*/
            if (jsonObject.get(MESSAGE_CATEGORY) != null)
                message.setCategory(jsonObject.getString(MESSAGE_CATEGORY));

            /*if (jsonObject.get(MESSAGE_ICON) != null)
                message.setIcon(jsonObject.getString(MESSAGE_ICON));*/

            if (jsonObject.get(MESSAGE_STRING) != null)
                message.setMessageString(jsonObject.getString(MESSAGE_STRING));

            if (jsonObject.get(PLAYGROUND_ID) != null)
                message.setPlaygroundID(jsonObject.getString("playgroundID"));

            if (jsonObject.get(MESSAGE_WRITTENBY_ID) != null)
                message.setWrittenByID(MESSAGE_WRITTENBY_ID);

            try {
                bufferedImage = ImageIO.read(ctx.uploadedFile("image").getContent());
            } catch (Exception e) {
                System.out.println("Server: No message image was added...");
            }


            if (Controller.getInstance().updatePlaygroundMessage(message).wasAcknowledged()) {
                ctx.status(200).result("Updated message with ID: " + message.getId());
                ctx.json(Controller.getInstance().getMessage(message.getId()));
                if (bufferedImage != null) {
                    Shared.saveMessageImage(message.getId(), bufferedImage);
                }
            } else {
                ctx.status(404).result("There was an error");
            }
        };
    }

}

