package i5.las2peer.services.mensaService;

import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.api.persistency.EnvelopeAccessDeniedException;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.persistency.EnvelopeOperationFailedException;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import io.swagger.annotations.*;
import net.minidev.json.JSONObject;
import org.apache.commons.io.IOUtils;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * las2peer-Mensa-Service
 * <p>
 * A las2peer service that can display the current menu of a canteen of the RWTH.
 */

@Api
@SwaggerDefinition(
        info = @Info(
                title = "las2peer Mensa Service",
                version = "1.0.0",
                description = "A las2peer Mensa Service for the RWTH canteen.",
                contact = @Contact(
                        name = "Alexander Tobias Neumann",
                        url = "https://las2peer.org",
                        email = "neumann@dbis.rwth-aachen.de"),
                license = @License(
                        name = "BSD-3",
                        url = "https://github.com/rwth-acis/las2peer-Mensa-Service/blob/master/LICENSE")))
@ServicePath("/mensa")

public class MensaService extends RESTService {

    private final static long SIX_HOURS_IN_MS = 6 * 60 * 60 * 1000L;
    private final static List<String> SUPPORTED_MENSAS = Arrays.asList("vita", "academica", "ahornstrasse",
            "templergraben");
    private final static String ENVELOPE_PREFIX = "mensa-";
    private final static String RATINGS_ENVELOPE_PREFIX = ENVELOPE_PREFIX + "ratings-";
    private final static String PICTURES_ENVELOPE_PREFIX = ENVELOPE_PREFIX + "pictures-";
    private final static String DISH_INDEX_ENVELOPE_NAME = ENVELOPE_PREFIX + "dishes";
    /**
     * Some dish names are not real dishes but status indicators like that the mensa counter is closed.
     */
    private final static List<String> DISH_NAME_BLACKLIST = Arrays.asList("closed", "geschlossen");
    private Date lastDishIndexUpdate;

    public static int ordinalIndexOf(String str, String substr, int n) {
        int pos = -1;
        do {
            pos = str.indexOf(substr, pos + 1);
        } while (n-- > 0 && pos != -1);
        return pos;
    }

    @Override
    protected void initResources() {
        super.initResources();
        getResourceConfig().register(PrematchingRequestFilter.class);
    }

  public Map<String, String> getCustomMessageDescriptions() {
    Map<String, String> descriptions = new HashMap<>();
    descriptions.put("SERVICE_CUSTOM_MESSAGE_1",
        "# Menu Queried for Mensa\n" +
            "## Format\n" +
            "```json\n" +
            "{ msg: \"<name of mensa>\"}\n" +
            "```\n" +
            "\n" +
            "## Examples\n" +
            "### Menu Requests by Mensa\n" +
            "```sql\n" +
            "SELECT JSON_EXTRACT(REMARKS,\"$.msg\") AS mensa, COUNT(*) FROM MESSAGE WHERE EVENT=\"SERVICE_CUSTOM_MESSAGE_1\" AND SOURCE_AGENT = '$SERVICE$' GROUP BY JSON_EXTRACT(REMARKS,\"$.msg\")\n" +
            "```\n" +
            "#### Visualization\n" +
            "Bar chart or pie chart.\n");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_2", "Menu queried for language. Format: Language in lang-country format.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_3", "Ratings queried for dish. Format: Name of dish.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_4", "Rating added for dish. Format: Name of dish.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_5", "Rating deleted for dish. Format: Name of dish.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_6", "Pictures queried for dish. Format: Name of dish.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_7", "Picture added for dish. Format: Name of dish.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_8", "Picture deleted for dish. Name of dish.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_10", "Menu successfully retrieved. Format: Menu as JSON.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_20", "Menu queried for unsupported mensa. Format: Name of unsupported mensa.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_40", "Time in ms to get return the menu. Format: Time is ms.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_41", "Time in ms to get return the rating for a dish. Format: Time is ms.");
    descriptions.put("SERVICE_CUSTOM_MESSAGE_42", "Time in ms to get return the pictures for a dish. Format: Time is ms.");
    return descriptions;
  }

  public JSONObject getMensaMenu(String language, String mensa) throws IOException {
        String mensaURL = "https://www.studierendenwerk-aachen.de/speiseplaene/";
        switch (language) {
            case "de-de":
                mensaURL += mensa + "-w.html";
                break;
            case "en-US":
                mensaURL += mensa + "-w-en.html";
                break;
            default:
                // German is the default language
                mensaURL += mensa + "-w.html";
                break;
        }
        URL url = new URL(mensaURL);
        URLConnection con = url.openConnection();
        InputStream in = con.getInputStream();
        String charset = "UTF-8"; // Assumption
        String mensaPage = IOUtils.toString(in, charset);
        int activeHeadlineIndex = mensaPage.indexOf("active-headline");
        if (activeHeadlineIndex == -1) {
            // special case: weekend is now
            // get next monday
            activeHeadlineIndex = ordinalIndexOf(mensaPage, "default-headline", 5);
        }
        mensaPage = mensaPage.substring(activeHeadlineIndex + 17);
        int nextDaySectionIndex = mensaPage.indexOf("default-headline") - 11;
        mensaPage = mensaPage.substring(0, nextDaySectionIndex);

        // menus
        String menu = mensaPage.substring(mensaPage.indexOf("table") - 1);
        String extra = menu.substring(menu.indexOf("</table>"));
        menu = menu.substring(0, menu.indexOf("</table>") + 8);
        JSONObject mensaMenus = new JSONObject();
        JSONObject mensaExtras = new JSONObject();
        while (menu.indexOf("<tr") > 0) {
            int menuTypeEnd = menu.indexOf("</span>");
            String menuType = menu.substring(menu.indexOf("<span class=\"menue-item menue-category\">") + 40,
                    menuTypeEnd);
            menu = menu.substring(menuTypeEnd + 1);

            String menuName = menu.substring(menu.indexOf("</span>"), menu.indexOf("<div class=\"nutr-info"));
            menuName = menuName.replaceAll("<sup>[\\sa-zA-Z0-9,]*</sup>", "");
            menuName = menuName.replaceAll("<[^>]*>", "").trim();
            menuName = menuName.replaceAll("^\\|\\s+", "");
            mensaMenus.appendField(menuType, menuName);
            int trIndex = menu.indexOf("</tr>");
            if (trIndex > 0) {
                menu = menu.substring(trIndex + 1);
            } else
                menu = "";
        }
        while (extra.indexOf("<tr") > 0) {
            int menuTypeEnd = extra.indexOf("</span>");
            String menuType = extra.substring(extra.indexOf("<span class=\"menue-item extra menue-category\">") + 46,
                    menuTypeEnd);
            extra = extra.substring(menuTypeEnd + 1);

            String menuName = extra.substring(extra.indexOf("</span>"), extra.indexOf("<div class=\"nutr-info"));
            menuName = menuName.replaceAll("<sup>[\\sa-zA-Z0-9,]*</sup>", "");
            menuName = menuName.replaceAll("<[^>]*>", " ").trim();
            menuName = menuName.replaceAll("^\\|\\s+", "");

            mensaExtras.appendField(menuType, menuName);

            int trIndex = extra.indexOf("</tr>");
            if (trIndex > 0) {
                extra = extra.substring(trIndex + 1);
            } else
                extra = "";
        }

        JSONObject m = new JSONObject();
        m.put("menus", mensaMenus);
        m.put("extras", mensaExtras);
        return m;
    }

    /**
     * This method returns the current menu of a canteen.
     *
     * @param mensa    A canteen of the RWTH.
     * @param language The user's language.
     * @return Returns a String containing the menu.
     */
    @GET
    @Path("/{mensa}")
    @ApiOperation(
            value = "Get the menu of a mensa",
            notes = "The mensa must be supported with the Studierendenwerk")
    @ApiResponses(
            value = {@ApiResponse(
                    code = HttpURLConnection.HTTP_OK,
                    message = "Menu received"),
                    @ApiResponse(
                            code = HttpURLConnection.HTTP_NOT_FOUND,
                            message = "Mensa not supported")})
    public Response getMensa(@PathParam("mensa") String mensa,
                             @HeaderParam("accept-language") @DefaultValue("de-de") String language,
                             @QueryParam("format") @DefaultValue("html") String format) {
        final long responseStart = System.currentTimeMillis();
        JSONObject mensaMenu;
        String returnString;
        if (!isMensaSupported(mensa)) {
            Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_20, mensa);
            return Response.status(Status.NOT_FOUND).entity("Mensa not supported!").build();
        }
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_1, mensa);
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_2, language);
        try {
            mensaMenu = getMensaMenu(language, mensa);
            if ("html".equals(format)) {
                returnString = convertToHtml(mensaMenu);
            } else {
                returnString = mensaMenu.toString();
            }
        } catch (IOException e) {
            return Response.status(Status.BAD_REQUEST).build();
        }
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_10, mensaMenu.toString());
        String responseContentType;
        switch (format) {
            case "html":
                responseContentType = MediaType.TEXT_HTML + ";charset=utf-8";
                break;
            default:
                responseContentType = MediaType.APPLICATION_JSON;
        }
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_40,
                String.valueOf(System.currentTimeMillis() - responseStart));
        return Response.ok().type(responseContentType).entity(returnString).build();
    }

    private String convertToHtml(JSONObject mensaMenu) {
        String returnString = "";
        JSONObject menus = (JSONObject) mensaMenu.get("menus");
        JSONObject extras = (JSONObject) mensaMenu.get("extras");
        for (String key : menus.keySet()) {
            if (key.equals("Tellergericht")) {
                returnString += "🍽 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.equals("Vegetarisch")) {
                returnString += "🥗 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.equals("Klassiker")) {
                returnString += "👨🏻‍🍳 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.equals("Empfehlung des Tages")) {
                returnString += "👌🏿👨🏿‍🍳 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.equals("Wok")) {
                returnString += "🥘 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.equals("Ofenkartoffel")) {
                returnString += "🥔 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.equals("Pasta")) {
                returnString += "🍝 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.contains("Pizza")) {
                returnString += "🍕 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.contains("Grill")) {
                returnString += "🥩 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.contains("Burger")) {
                returnString += "🍔 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.contains("Sandwich")) {
                returnString += "🥪 " + key + ": " + menus.getAsString(key) + "\n";
            } else if (key.contains("Flammengrill")) {
                returnString += "🔥 " + key + ": " + menus.getAsString(key) + "\n";
            } else {
                returnString += key + ": " + menus.getAsString(key) + "\n";
            }
        }
        returnString += "___\n";
        for (String key : extras.keySet()) {
            if (key.equals("Hauptbeilagen")) {
                returnString += "" + key + ": " + extras.getAsString(key) + "\n";
            } else if (key.equals("Nebenbeilagen")) {
                returnString += "" + key + ": " + extras.getAsString(key) + "\n";
            } else {
                returnString += key + ": " + extras.getAsString(key) + "\n";
            }
        }
        return returnString;
    }

    /**
     * Command management for Slack.
     *
     * @param form Parameters provided by the slash command.
     * @return Returns the result for the specific command.
     */
    @POST
    @Path("/command")
    @Produces(MediaType.TEXT_HTML + ";charset=utf-8")
    @Consumes("application/x-www-form-urlencoded")
    @ApiResponses(
            value = {@ApiResponse(
                    code = HttpURLConnection.HTTP_OK,
                    message = "Command executed.")})
    @ApiOperation(
            value = "Perform a command",
            notes = "")
    public Response postTemplate(MultivaluedMap<String, String> form) {
        String cmd = form.getFirst("command");
        String text = form.getFirst("text");
        String response = "";
        if (cmd.equals("/mensa")) {
            if (isMensaSupported(text)) {
                response = (String) getMensa(text, "de-de", "html").getEntity();
                Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_10, response);
            } else {
                response = "Mensa not supported";
                Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_20, text);
            }
        }
        return Response.ok().entity(response).build();
    }

    /**
     * Retrieve all ratings for a dish.
     *
     * @param dish Name of the dish.
     * @return JSON encoded list of ratings.
     */
    @GET
    @Path("/dishes/{dish}/ratings")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getRatings(@PathParam("dish") String dish) throws EnvelopeOperationFailedException {
        final long responseStart = System.currentTimeMillis();
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_3, dish);
        HashMap<String, Rating> response = getRatingsForDish(dish);
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_41,
                String.valueOf(System.currentTimeMillis() - responseStart));
        return Response.ok().entity(response).build();
    }

    /**
     * Add a rating for a dish.
     *
     * @param dish Name of the dish.
     * @return JSON encoded list of ratings.
     */
    @POST
    @Path("/dishes/{dish}/ratings")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("authenticated")
    public Response addRating(@PathParam("dish") String dish, Rating rating)
            throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_4, dish);
        HashMap<String, Rating> response = storeRating(dish, rating);
        return Response.ok().entity(response).build();
    }

    /**
     * Delete a rating for a dish.
     *
     * @param dish Name of the dish.
     * @return JSON encoded list of ratings.
     */
    @DELETE
    @Path("/dishes/{dish}/ratings")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("authenticated")
    public Response deleteRating(@PathParam("dish") String dish)
            throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_5, dish);
        HashMap<String, Rating> response = removeRating(dish);
        return Response.ok().entity(response).build();
    }

    /**
     * Retrieve all pictures for a dish.
     *
     * @param dish Name of the dish.
     * @return JSON encoded list of pictures.
     */
    @GET
    @Path("/dishes/{dish}/pictures")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getPictures(@PathParam("dish") String dish) throws EnvelopeOperationFailedException {
        final long responseStart = System.currentTimeMillis();
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_6, dish);
        HashMap<String, ArrayList<Picture>> response = getPicturesForDish(dish);
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_42,
                String.valueOf(System.currentTimeMillis() - responseStart));
        return Response.ok().entity(response).build();
    }

    /**
     * Add a picture for a dish.
     *
     * @param dish Name of the dish.
     * @return JSON encoded list of pictures.
     */
    @POST
    @Path("/dishes/{dish}/pictures")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("authenticated")
    public Response addPicture(@PathParam("dish") String dish, Picture picture)
            throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_7, dish);
        Map<String, ArrayList<Picture>> response = storePicture(dish, picture);
        return Response.ok().entity(response).build();
    }

    /**
     * Delete a picture for a dish.
     *
     * @param dish Name of the dish.
     * @return JSON encoded list of pictures.
     */
    @DELETE
    @Path("/dishes/{dish}/pictures")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @RolesAllowed("authenticated")
    public Response deletePicture(@PathParam("dish") String dish, Picture picture)
            throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_8, dish);
        Map<String, ArrayList<Picture>> response = removePicture(dish, picture);
        return Response.ok().entity(response).build();
    }

    /**
     * Get a list of dishes that have been served in any mensa in the past.
     *
     * @return A list of strings with dish names.
     */
    @GET
    @Path("/dishes")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response getDishes() throws EnvelopeOperationFailedException {
        Set<String> dishes = getDishIndex();
        List<String> response = new ArrayList<>(dishes);
        Collections.sort(response);
        return Response.ok().entity(response).build();
    }

    private boolean isMensaSupported(String mensa) {
        return SUPPORTED_MENSAS.contains(mensa);
    }

    private HashMap<String, Rating> getRatingsForDish(String dish) throws EnvelopeOperationFailedException {
        try {
            Envelope env = getOrCreateRatingsEnvelopeForDish(dish);
            return (HashMap<String, Rating>) env.getContent();
        } catch (EnvelopeAccessDeniedException e) {
            return new HashMap<>();
        }
    }

    private HashMap<String, ArrayList<Picture>> getPicturesForDish(String dish) throws EnvelopeOperationFailedException {
        try {
            Envelope env = getOrCreatePicturesEnvelopeForDish(dish);
            return (HashMap<String, ArrayList<Picture>>) env.getContent();
        } catch (EnvelopeAccessDeniedException e) {
            return new HashMap<>();
        }
    }

    private Set<String> getDishIndex() throws EnvelopeOperationFailedException {
        try {
            Envelope env = getOrCreateDishIndexEnvelope();
            return (Set<String>) env.getContent();
        } catch (EnvelopeAccessDeniedException e) {
            return new HashSet<>();
        }
    }

    private HashMap<String, Rating> storeRating(String dish, Rating rating)
            throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
        UserAgent userAgent = (UserAgent) Context.get().getMainAgent();
        String username = userAgent.getLoginName();
        rating.author = username;
        rating.timestamp = getCurrentTimestamp();
        Envelope envelope = getOrCreateRatingsEnvelopeForDish(dish);
        HashMap<String, Rating> ratings = (HashMap<String, Rating>) envelope.getContent();
        ratings.put(username, rating);
        envelope.setContent(ratings);
        Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
        return ratings;
    }

    private HashMap<String, Rating> removeRating(String dish)
            throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
        UserAgent userAgent = (UserAgent) Context.get().getMainAgent();
        String username = userAgent.getLoginName();
        Envelope envelope = getOrCreateRatingsEnvelopeForDish(dish);
        HashMap<String, Rating> ratings = (HashMap<String, Rating>) envelope.getContent();
        ratings.remove(username);
        envelope.setContent(ratings);
        Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
        return ratings;
    }

    private Map<String, ArrayList<Picture>> storePicture(String dish, Picture picture)
            throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
        UserAgent userAgent = (UserAgent) Context.get().getMainAgent();
        String username = userAgent.getLoginName();
        picture.author = username;
        Envelope envelope = getOrCreatePicturesEnvelopeForDish(dish);
        HashMap<String, ArrayList<Picture>> pictures = (HashMap<String, ArrayList<Picture>>) envelope.getContent();
        if (!pictures.containsKey(username)) {
            pictures.put(username, new ArrayList<>());
        }
        pictures.get(username).add(picture);
        envelope.setContent(pictures);
        Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
        return pictures;
    }

    private Map<String, ArrayList<Picture>> removePicture(String dish, Picture picture)
            throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
        UserAgent userAgent = (UserAgent) Context.get().getMainAgent();
        String username = userAgent.getLoginName();
        Envelope envelope = getOrCreatePicturesEnvelopeForDish(dish);
        HashMap<String, ArrayList<Picture>> pictures = (HashMap<String, ArrayList<Picture>>) envelope.getContent();
        pictures.get(username).remove(picture);
        envelope.setContent(pictures);
        Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
        return pictures;
    }

    private Envelope getOrCreateRatingsEnvelopeForDish(String dish)
            throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
        String envelopeName = RATINGS_ENVELOPE_PREFIX + dish;
        return getOrCreateEnvelope(envelopeName, new HashMap<String, Rating>());
    }

    private Envelope getOrCreatePicturesEnvelopeForDish(String dish)
            throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
        String envelopeName = PICTURES_ENVELOPE_PREFIX + dish;
        return getOrCreateEnvelope(envelopeName, new HashMap<String, ArrayList<Picture>>());
    }

    private Envelope getOrCreateDishIndexEnvelope()
            throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
        return getOrCreateEnvelope(DISH_INDEX_ENVELOPE_NAME, new HashSet<>());
    }

    private Envelope getOrCreateEnvelope(String name, Serializable defaultContent)
            throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
        try {
            return Context.get().requestEnvelope(name);
        } catch (EnvelopeNotFoundException e) {
            Envelope envelope = Context.get().createEnvelope(name, Context.get().getServiceAgent());
            envelope.setContent(defaultContent);
            envelope.setPublic();
            Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
            return envelope;
        }
    }

    private String getCurrentTimestamp() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(TimeZone.getTimeZone("CET"));
        return sdf.format(date);
    }

    void updateDishes() {
        // if the new dishes have not been persisted within the last six hours we do so now
        if (lastDishIndexUpdate == null
                || Math.abs(lastDishIndexUpdate.getTime() - new Date().getTime()) > SIX_HOURS_IN_MS) {
            Date previousLastDishIndexUpdate = this.lastDishIndexUpdate;
            lastDishIndexUpdate = new Date();
            try {
                this.saveDishesToIndex();
            } catch (EnvelopeAccessDeniedException | EnvelopeOperationFailedException e) {
                e.printStackTrace();
                this.lastDishIndexUpdate = previousLastDishIndexUpdate;
            }
        }
    }

    /**
     * Get all dishes from all mensa menus and save them to the distributed storage.
     *
     * @throws EnvelopeAccessDeniedException
     * @throws EnvelopeOperationFailedException
     */
    private void saveDishesToIndex() throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
        System.out.println("Saving dishes to index...");
        Envelope envelope = this.getOrCreateDishIndexEnvelope();
        HashSet<String> dishes = (HashSet<String>) envelope.getContent();
        for (String mensa : SUPPORTED_MENSAS) {
            JSONObject menu;
            try {
                menu = this.getMensaMenu("en-US", mensa);
            } catch (IOException e) {
                menu = new JSONObject();
                menu.put("menus", new JSONObject());
            }
            JSONObject newDishes = (JSONObject) menu.get("menus");
            for (Object dishObj : newDishes.values()) {
                String dish = (String) dishObj;
                if (!DISH_NAME_BLACKLIST.contains(dish)) {
                    // only save if the string represents a real dish
                    dishes.add(dish);
                }
            }
        }

        envelope.setContent(dishes);
        Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
    }

    private static class Rating implements Serializable {
        public String author;
        public int stars;
        public String comment;
        public String mensa;
        public String timestamp;

        Rating() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Rating rating = (Rating) o;
            return stars == rating.stars &&
                    author.equals(rating.author) &&
                    Objects.equals(comment, rating.comment) &&
                    mensa.equals(rating.mensa) &&
                    timestamp.equals(rating.timestamp);
        }

        @Override
        public int hashCode() {
            return Objects.hash(author, stars, comment, mensa, timestamp);
        }
    }

    private static class Picture implements Serializable {
        public String image;
        public String author;

        Picture() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Picture picture = (Picture) o;
            return image.equals(picture.image) &&
                    author.equals(picture.author);
        }

        @Override
        public int hashCode() {
            return Objects.hash(image, author);
        }
    }

}
