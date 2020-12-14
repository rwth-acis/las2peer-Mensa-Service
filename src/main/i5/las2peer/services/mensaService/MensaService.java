package i5.las2peer.services.mensaService;

import i5.las2peer.api.Context;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.persistency.EnvelopeOperationFailedException;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import i5.las2peer.services.mensaService.database.SQLDatabase;
import i5.las2peer.services.mensaService.database.SQLDatabaseType;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

/**
 * las2peer-Mensa-Service
 * <p>
 * A las2peer service that can display the current menu of a canteen of the
 * RWTH.
 */

@Api
@SwaggerDefinition(
  info = @Info(
    title = "las2peer Mensa Service",
    version = "1.0.2",
    description = "A las2peer Mensa Service for the RWTH canteen.",
    contact = @Contact(
      name = "Alexander Tobias Neumann",
      url = "https://las2peer.org",
      email = "neumann@dbis.rwth-aachen.de"
    ),
    license = @License(
      name = "BSD-3",
      url = "https://github.com/rwth-acis/las2peer-Mensa-Service/blob/master/LICENSE"
    )
  )
)
@ServicePath("/mensa")
@ManualDeployment
public class MensaService extends RESTService {

  private static final long SIX_HOURS_IN_MS = 6 * 60 * 60 * 1000L;
  private static final long ONE_DAY_IN_MS = 24 * 60 * 60 * 1000L;
  // private static final List<String> SUPPORTED_MENSAS = Arrays.asList(
  //   "vita",
  //   "academica",
  //   "ahornstrasse"
  // );
  // private static final String ENVELOPE_PREFIX = "mensa-";
  // private static final String RATINGS_ENVELOPE_PREFIX =
  //   ENVELOPE_PREFIX + "ratings-";
  // private static final String PICTURES_ENVELOPE_PREFIX =
  //   ENVELOPE_PREFIX + "pictures-";
  // private static final String DISH_INDEX_ENVELOPE_NAME =
  //   ENVELOPE_PREFIX + "dishes";

  private static final String OPEN_MENSA_API_ENDPOINT =
    "https://openmensa.org/api/v2";

  private Map<Integer, Date> lastDishUpdate;
  private Date lastMensasUpdate;

  private String databaseName;
  private int databaseTypeInt = 1;
  private SQLDatabaseType databaseType;
  private String databaseHost;
  private int databasePort;
  private String databaseUser;
  private String databasePassword;
  private SQLDatabase database; // The database instance to write to.

  public MensaService() {
    super();
    setFieldValues();
    this.databaseType = SQLDatabaseType.getSQLDatabaseType(databaseTypeInt);
    this.lastDishUpdate = new HashMap<>();
    this.database =
      new SQLDatabase(
        this.databaseType,
        this.databaseUser,
        this.databasePassword,
        this.databaseName,
        this.databaseHost,
        this.databasePort
      );
    try {
      System.out.println("Connecting to las2peermon...");
      Connection con = database.getDataSource().getConnection();
      System.out.println("Database connection successfull");
      con.close();
    } catch (SQLException e) {
      e.printStackTrace();
      System.out.println("Failed to connect to Database: " + e.getMessage());
    }
  }

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

  @Override
  public Map<String, String> getCustomMessageDescriptions() {
    Map<String, String> descriptions = new HashMap<>();
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_1",
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
      "Bar chart or pie chart.\n"
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_2",
      "Menu queried for language. Format: Language in lang-country format."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_3",
      "Ratings queried for dish. Format: Name of dish."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_4",
      "Rating added for dish. Format: Name of dish."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_5",
      "Rating deleted for dish. Format: Name of dish."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_6",
      "Pictures queried for dish. Format: Name of dish."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_7",
      "Picture added for dish. Format: Name of dish."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_8",
      "Picture deleted for dish. Name of dish."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_10",
      "Menu successfully retrieved. Format: Menu as JSON."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_20",
      "Menu queried for unsupported mensa. Format: Name of unsupported mensa."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_40",
      "Time in ms to get return the menu. Format: Time is ms."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_41",
      "Time in ms to get return the rating for a dish. Format: Time is ms."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_42",
      "Time in ms to get return the pictures for a dish. Format: Time is ms."
    );
    descriptions.put("SERVICE_CUSTOM_MESSAGE_43", "update Dish index");
    return descriptions;
  }

  public JSONArray getMensaMenu(int mensaID) throws IOException {
    JSONParser jsonParser = new JSONParser(JSONParser.MODE_PERMISSIVE);
    String urlString = OPEN_MENSA_API_ENDPOINT + "/canteens/";
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Calendar cal = Calendar.getInstance();
    int weekday = cal.get(Calendar.DAY_OF_WEEK);
    String day;
    JSONArray menu = new JSONArray();

    if (weekday == 1) { // Sunday
      Date monday = new Date(new Date().getTime() + ONE_DAY_IN_MS);
      day = dateFormat.format(monday);
    } else if (weekday == 7) { // Saturday
      Date sunday = new Date(new Date().getTime() + 2 * ONE_DAY_IN_MS);
      day = dateFormat.format(sunday);
    } else {
      day = dateFormat.format(new Date());
    }

    urlString += mensaID + "/days/" + day + "/meals";

    try {
      URL url = new URL(urlString);
      URLConnection con = url.openConnection();
      con.addRequestProperty("Content-type", "application/json");
      menu = (JSONArray) jsonParser.parse(con.getInputStream());
    } catch (ParseException e) {
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("Error on URL Connection to OpenMensa API");
      throw e;
    }
    return menu;
  }

  private ResultSet findMensasByName(String mensaName) {
    Connection dbConnection = null;
    PreparedStatement statement = null;
    ResultSet res;
    try {
      dbConnection = getDatabaseConnection();
      statement =
        dbConnection.prepareStatement("SELECT * FROM mensas WHERE name LIKE ?");
      statement.setString(1, "%" + mensaName + "%");
      res = statement.executeQuery();
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  // hard coded IDs of mensas in Aachen
  // for old mensa function
  private int getMensaId(String mensaName) {
    switch (mensaName) {
      case "vita":
        return 96;
      case "ahornstrasse":
        return 95;
      case "academica":
        return 187;
      default:
        return -1;
    }
  }

  /**
   * This method returns the current menu of supported canteens.
   *
   * @param body Body needs to contain at least the name of the mensa.
   * @return Returns a JSON String containing the menu under the text property.
   */
  @POST
  @Path("/menu")
  @Consumes(MediaType.TEXT_HTML)
  @Produces(MediaType.APPLICATION_JSON)
  @ApiOperation(
    value = "REPLACE THIS WITH AN APPROPRIATE FUNCTION NAME",
    notes = "REPLACE THIS WITH YOUR NOTES TO THE FUNCTION"
  )
  @ApiResponses(
    value = {
      @ApiResponse(
        code = HttpURLConnection.HTTP_OK,
        message = "REPLACE THIS WITH YOUR OK MESSAGE"
      ),
    }
  )
  public Response getMenu(String body) {
    JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
    JSONObject chatResponse = new JSONObject();
    int id;
    final int maxEntries = 20;

    try {
      JSONObject bodyJson = (JSONObject) p.parse(body);
      String mensa = bodyJson.getAsString("mensa");
      if (mensa == null) throw new ChatException(
        "Please specify the mensa, for which you want to get the menu."
      );

      ResultSet mensas = findMensasByName(mensa);
      if (!mensas.next()) throw new ChatException(
        "Sorry, I could not find a mensa with that name. 💁"
      );

      String first = mensas.getString("name"); // first entry
      id = mensas.getInt("id");
      String response =
        "I found the following mensas for " + mensa + ": \n1. " + first + "\n";

      int i = 2;
      while (mensas.next() && i < maxEntries) { //at least 2 entries
        response += i + ". " + mensas.getString("name") + "\n";
        i++;
      }
      switch (i) {
        case 2:
          String menu = createMenuChatResponse(first, id);
          response = menu;
          saveDishesToIndex(id);
          break;
        case maxEntries:
          mensas.last();
          int total = mensas.getRow();
          if (total - maxEntries > 0) {
            response += "and " + (total - maxEntries) + " more...\n";
            response +=
              "Specify the name of your mensa more clearly, if your mensa is not on the list\n";
          }
          break;
      }
      chatResponse.appendField("text", response);
      return Response.ok().entity(chatResponse).build();
    } catch (ChatException e) {
      chatResponse.appendField("text", e.getMessage());
      return Response.ok().entity(chatResponse).build();
    } catch (Exception e) {
      e.printStackTrace();
      chatResponse.appendField("text", "Sorry, a problem occured 🙁");
      return Response.ok().entity(chatResponse).build();
    }
  }

  /**Gets the menu for the mensa and formats it as a string which can be presented in chat
   * @param name The name of the mensa
   * @param id The id of the mensa for the OpenMensa API (https://doc.openmensa.org/api/v2)
   */
  private String createMenuChatResponse(String name, int id)
    throws SQLException, ChatException {
    String MESSAGE_HEAD = "";
    String weekday = new SimpleDateFormat("EEEE").format(new Date());

    if ("Sunday".equals(weekday) || "Saturday".equals(weekday)) { //If weekend we try to fetch the menu for following monday (mensas are typically closed on weekends)
      MESSAGE_HEAD +=
        "Please note that the mensa is closed on week-ends. This is the menu for Monday\n";
      weekday = "Monday";
    }
    MESSAGE_HEAD +=
      "Here is the menu for mensa " + name + " on " + weekday + " : \n \n";

    try {
      JSONArray mensaMenu = getMensaMenu(id);
      String returnString = convertToHtml(mensaMenu);
      return MESSAGE_HEAD + returnString;
    } catch (IOException e) {
      throw new ChatException(
        "Could not get the menu for mensa " +
        name +
        ".\n The mensa is probably closed on " +
        weekday +
        ", or no menu has been published yet 😔"
      );
    }
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
    notes = "The mensa must be supported with the Studierendenwerk in Aachen."
  )
  @ApiResponses(
    value = {
      @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Menu received"),
      @ApiResponse(
        code = HttpURLConnection.HTTP_NOT_FOUND,
        message = "Mensa not supported"
      ),
    }
  )
  public Response getMensa(
    @PathParam("mensa") String mensa,
    @HeaderParam("accept-language") @DefaultValue("de-de") String language,
    @QueryParam("format") @DefaultValue("html") String format
  ) {
    final long responseStart = System.currentTimeMillis();
    int mensaID;
    JSONArray mensaMenu;
    String returnString;

    if (!isMensaSupported(mensa)) {
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_20, mensa);
      return Response
        .status(Status.NOT_FOUND)
        .entity("Mensa not supported!")
        .build();
    }
    Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_1, mensa);
    Context
      .get()
      .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_2, language);
    try {
      mensaID = getMensaId(mensa);
      if (mensaID == -1) {
        throw new IOException("Mensa Id not found");
      }

      mensaMenu = getMensaMenu(mensaID);
      if ("html".equals(format)) {
        returnString = convertToHtml(mensaMenu);
      } else {
        returnString = mensaMenu.toString();
      }
      saveDishesToIndex(mensaID);
    } catch (IOException e) {
      return Response
        .status(Status.CONFLICT)
        .entity("Could not get the menu for mensa ")
        .build();
    } catch (Exception e) {
      return Response.status(Status.CONFLICT).entity(e.getMessage()).build();
    }

    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_10,
        mensaMenu.toString()
      );
    String responseContentType;
    switch (format) {
      case "html":
        responseContentType = MediaType.TEXT_HTML + ";charset=utf-8";
        break;
      default:
        responseContentType = MediaType.APPLICATION_JSON;
    }

    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_40,
        String.valueOf(System.currentTimeMillis() - responseStart)
      );
    return Response.ok().type(responseContentType).entity(returnString).build();
  }

  private String convertToHtml(JSONArray mensaMenu) {
    String returnString = "";
    JSONArray menus = mensaMenu;

    for (Object o : menus) {
      JSONObject menuItem = (JSONObject) o;
      String type = menuItem.getAsString("category");
      String dish = menuItem.getAsString("name");
      if (type.equals("Tellergericht") || type.contains("Entrée")) {
        returnString += "🍽 " + type + ": " + dish + "\n";
      } else if (type.equals("Vegetarisch") || type.contains("Végétarien")) {
        returnString += "🥗 " + type + ": " + dish + "\n";
      } else if (type.equals("Klassiker") || type.contains("Protidique")) {
        returnString += "👨🏻‍🍳 " + type + ": " + dish + "\n";
      } else if (type.equals("Empfehlung des Tages")) {
        returnString += "👌🏿👨🏿‍🍳 " + type + ": " + dish + "\n";
      } else if (type.equals("Wok")) {
        returnString += "🥘 " + type + ": " + dish + "\n";
      } else if (type.equals("Ofenkartoffel")) {
        returnString += "🥔 " + type + ": " + dish + "\n";
      } else if (type.equals("Pasta")) {
        returnString += "🍝 " + type + ": " + dish + "\n";
      } else if (type.contains("Pizza")) {
        returnString += "🍕 " + type + ": " + dish + "\n";
      } else if (type.contains("Grill")) {
        returnString += "🥩 " + type + ": " + dish + "\n";
      } else if (type.contains("Burger")) {
        returnString += "🍔 " + type + ": " + dish + "\n";
      } else if (type.contains("Sandwich")) {
        returnString += "🥪 " + type + ": " + dish + "\n";
      } else if (type.contains("Flammengrill")) {
        returnString += "🔥 " + type + ": " + dish + "\n";
      } else {
        returnString += type + ": " + dish + "\n";
      }
    }
    returnString += "___\n";
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
    value = {
      @ApiResponse(
        code = HttpURLConnection.HTTP_OK,
        message = "Command executed."
      ),
    }
  )
  @ApiOperation(value = "Perform a command", notes = "")
  public Response postTemplate(MultivaluedMap<String, String> form) {
    String cmd = form.getFirst("command");
    String text = form.getFirst("text");
    String response = "";
    if (cmd.equals("/mensa")) {
      response = (String) getMensa(text, "de-de", "html").getEntity();
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_10, response);
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
  public Response getRatings(@PathParam("dish") String dish) {
    Object response = null;
    final long responseStart = System.currentTimeMillis();
    Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_3, dish);

    try { //get Ratings from distributed storage
      JSONArray reviews = new JSONArray();
      JSONObject review;
      Connection con = getDatabaseConnection();
      PreparedStatement s = con.prepareStatement(
        "SELECT * FROM reviews LEFT JOIN (dishes,mensas) ON (reviews.dishid =dishes.id AND mensas.id=reviews.mensaId) WHERE dishes.name=?"
      );
      s.setString(1, dish);
      ResultSet res = s.executeQuery();
      while (res.next()) {
        review = new JSONObject();
        review.put("mensa", res.getString("name"));
        review.put("stars", res.getString("stars"));
        review.put("comment", res.getString("comment"));
        reviews.add(review);
      }
      response = reviews;
      Context
        .get()
        .monitorEvent(
          MonitoringEvent.SERVICE_CUSTOM_MESSAGE_41,
          String.valueOf(System.currentTimeMillis() - responseStart)
        );
    } catch (SQLException e) {
      e.printStackTrace();
      response = e.getMessage();
    }
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
  public Response addRating(@PathParam("dish") String dish, Rating rating) {
    Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_4, dish);
    Object response = null;
    try {
      Connection con = getDatabaseConnection();
      PreparedStatement s = con.prepareStatement(
        "SELECT (id) FROM dishes WHERE name=?"
      );
      s.setString(1, dish);
      ResultSet res = s.executeQuery();
      if (!res.next()) {
        return Response.ok().entity("dish not found in  db").build();
      }
      int dishId = res.getInt("id");
      s =
        con.prepareStatement(
          "INSERT INTO reviews (author,mensaId,dishId,timestamp,stars,comment) VALUES (?,?,?,?,?,?)"
        );
      s.setString(1, rating.author);
      s.setInt(2, rating.mensaId);
      s.setInt(3, dishId);
      s.setDate(4, new java.sql.Date(new Date().getTime()));
      s.setInt(5, rating.stars);
      s.setString(6, rating.comment);
      s.executeUpdate();
      s.close();
      response = rating;
    } catch (Exception e) {
      e.printStackTrace();
      response = e.getMessage();
    }
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
  public Response deleteRating(@PathParam("dish") int dishId) {
    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_5,
        String.valueOf(dishId)
      );
    try {
      Connection con = getDatabaseConnection();
      PreparedStatement s = con.prepareStatement(
        "DELETE FROM reviews WHERE id=?"
      );
      s.setInt(1, dishId);
      s.executeUpdate();
      s.close();
      return Response.ok().build();
    } catch (SQLException e) {
      e.printStackTrace();
      return Response.ok().entity(e.getMessage()).build();
    }
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
  public Response getPictures(@PathParam("dish") String dish)
    throws EnvelopeOperationFailedException {
    final long responseStart = System.currentTimeMillis();
    Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_6, dish);

    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_42,
        String.valueOf(System.currentTimeMillis() - responseStart)
      );
    return Response.ok().entity("response").build();
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
  public Response addPicture(@PathParam("dish") String dish, Picture picture) {
    Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_7, dish);

    return Response.ok().entity("response").build();
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
    JSONArray dishes = new JSONArray();

    try {
      Connection con = getDatabaseConnection();
      ResultSet res = con
        .prepareStatement("SELECT DISTINCT (name) FROM dishes")
        .executeQuery();
      while (res.next()) {
        dishes.appendElement(res.getString("name"));
      }
      return Response.ok().entity(dishes).build();
    } catch (Exception e) {
      return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }
  }

  private boolean isMensaSupported(String mensa) {
    try {
      ResultSet res = findMensasByName(mensa);
      return res.next(); //true if at least one entry matches the input
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static class Rating implements Serializable {

    public String author;
    public int stars;
    public String comment;
    public int mensaId;
    public String timestamp;

    Rating() {}

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Rating rating = (Rating) o;
      return (
        stars == rating.stars &&
        author.equals(rating.author) &&
        Objects.equals(comment, rating.comment) &&
        mensaId == rating.mensaId &&
        timestamp.equals(rating.timestamp)
      );
    }

    @Override
    public int hashCode() {
      return Objects.hash(author, stars, comment, mensaId, timestamp);
    }
  }

  private static class Picture implements Serializable {

    public String image;
    public String author;

    Picture() {}

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Picture picture = (Picture) o;
      return image.equals(picture.image) && author.equals(picture.author);
    }

    @Override
    public int hashCode() {
      return Objects.hash(image, author);
    }
  }

  /** Exceptions ,with messages, that should be returned in Chat */
  protected static class ChatException extends Exception {

    protected ChatException(String message) {
      super(message);
    }
  }

  /**Saves the dishes for a  menu from a given mensa in the datbase
   * @param mensaId Id of the mensa for which the menu was fetched
   */
  private void saveDishesToIndex(int mensaId) {
    Date lastUpdate = this.lastDishUpdate.get(mensaId);
    System.out.println("Last dish update: " + lastUpdate);
    if (
      lastUpdate != null &&
      Math.abs(lastUpdate.getTime() - new Date().getTime()) < SIX_HOURS_IN_MS
    ) {
      System.out.println("No need to update dishes");
      return;
    }
    System.out.println("Saving dishes to index...");
    lastDishUpdate.put(mensaId, new Date());
    try {
      JSONArray menu = getMensaMenu(mensaId);
      Connection con = getDatabaseConnection();
      menu.forEach(
        menuitem -> {
          try {
            addDishEntry((JSONObject) menuitem, con, mensaId);
          } catch (SQLException e) {
            e.printStackTrace();
          }
        }
      );
    } catch (Exception e) {
      System.out.println("Error couldnt save dishes");
      e.printStackTrace();
    }
  }

  /** Updates the mensas in the database*/
  protected void fetchMensas() {
    // only update the mensas once a month. Mensas do not change that often as
    // mentioned on https://doc.openmensa.org/api/v2/canteens/
    if (
      lastMensasUpdate != null &&
      (
        Math.abs(new Date().getTime() - lastMensasUpdate.getTime()) <
        30 *
        ONE_DAY_IN_MS
      )
    ) {
      return;
    }
    lastMensasUpdate = new Date();
    System.out.println("Updating mensas...");
    JSONParser jsonParser = new JSONParser(JSONParser.MODE_PERMISSIVE);
    Connection dbConnection = null;
    String urlString = OPEN_MENSA_API_ENDPOINT + "/canteens/";
    JSONArray mensas;
    int updates = 0; // number of entries modified
    Integer totalPages = 0;

    try {
      dbConnection = getDatabaseConnection();
      URL url = new URL(urlString);
      URLConnection con = url.openConnection();
      InputStream in = con.getInputStream();
      totalPages = Integer.parseInt(con.getHeaderField("x-total-pages"));
      System.out.println(totalPages + " pages to process");

      mensas = (JSONArray) jsonParser.parse(in);

      for (Object mensa : mensas) {
        updates += addOrUpdateMensaEntry((JSONObject) mensa, dbConnection);
      }

      if (totalPages == 1) {
        return;
      }

      for (Integer page = 1; page <= totalPages; page++) {
        urlString =
          OPEN_MENSA_API_ENDPOINT + "/canteens?page=" + page.toString();
        url = new URL(urlString);
        con = url.openConnection();
        mensas = (JSONArray) jsonParser.parse(con.getInputStream());

        for (Object mensa : mensas) {
          updates += addOrUpdateMensaEntry((JSONObject) mensa, dbConnection);
        }
      }
      System.out.println(updates + " entries modified");
    } catch (IOException | ParseException | SQLException e) {
      e.printStackTrace();
    } finally {
      try {
        if (dbConnection != null && !dbConnection.isClosed()) {
          dbConnection.close();
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }

  /** Updates a mensa entry in the database
   * @param obj the entry, which sould be modified
   * @return number of updates
   */
  private int addOrUpdateMensaEntry(JSONObject obj, Connection con)
    throws SQLException {
    PreparedStatement statement = con.prepareStatement(
      "REPLACE INTO mensas VALUES(?,?,?,?)"
    );
    statement.setInt(1, Integer.parseInt(obj.getAsString("id")));
    statement.setString(2, obj.getAsString("name"));
    statement.setString(3, obj.getAsString("city"));
    statement.setString(4, obj.getAsString("address"));
    int updated = statement.executeUpdate();
    statement.close();
    return updated;
  }

  /** Updates a dish entry in the database
   * @param obj the entry, which sould be modified
   * @param con database connection
   * @param mensaId the id of the mensa at which the dish is served
   * @return number of updates
   */
  private int addDishEntry(JSONObject obj, Connection con, int mensaId)
    throws SQLException {
    PreparedStatement statement = con.prepareStatement(
      "INSERT IGNORE INTO dishes VALUES  (?,?,?,?)"
    );
    statement.setInt(1, Integer.parseInt(obj.getAsString("id")));
    statement.setInt(2, mensaId);

    statement.setString(3, obj.getAsString("name"));
    statement.setString(4, obj.getAsString("category"));
    int updated = statement.executeUpdate();
    statement.close();
    return updated;
  }

  /** Returns a connection to the database */
  private Connection getDatabaseConnection() throws SQLException {
    MensaService service = (MensaService) Context.get().getService();

    return service.database.getDataSource().getConnection();
  }
  //old implementation using envelopes
  // /**
  //  * Retrieve all ratings for a dish.
  //  *
  //  * @param dish Name of the dish.
  //  * @return JSON encoded list of ratings.
  //  */
  // @GET
  // @Path("/dishes/{dish}/ratings")
  // @Produces(MediaType.APPLICATION_JSON)
  // @Consumes(MediaType.APPLICATION_JSON)
  // public Response getRatings(@PathParam("dish") String dish)
  //   throws EnvelopeOperationFailedException {
  //   final long responseStart = System.currentTimeMillis();
  //   Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_3, dish);
  //   HashMap<String, Rating> response = getRatingsForDish(dish);
  //   Context
  //     .get()
  //     .monitorEvent(
  //       MonitoringEvent.SERVICE_CUSTOM_MESSAGE_41,
  //       String.valueOf(System.currentTimeMillis() - responseStart)
  //     );
  //   return Response.ok().entity(response).build();
  // }

  // /**
  //  * Add a rating for a dish.
  //  *
  //  * @param dish Name of the dish.
  //  * @return JSON encoded list of ratings.
  //  */
  // @POST
  // @Path("/dishes/{dish}/ratings")
  // @Produces(MediaType.APPLICATION_JSON)
  // @Consumes(MediaType.APPLICATION_JSON)
  // @RolesAllowed("authenticated")
  // public Response addRating(@PathParam("dish") String dish, Rating rating)
  //   throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
  //   Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_4, dish);
  //   HashMap<String, Rating> response = storeRating(dish, rating);
  //   return Response.ok().entity(response).build();
  // }

  // /**
  //  * Delete a rating for a dish.
  //  *
  //  * @param dish Name of the dish.
  //  * @return JSON encoded list of ratings.
  //  */
  // @DELETE
  // @Path("/dishes/{dish}/ratings")
  // @Produces(MediaType.APPLICATION_JSON)
  // @Consumes(MediaType.APPLICATION_JSON)
  // @RolesAllowed("authenticated")
  // public Response deleteRating(@PathParam("dish") String dish)
  //   throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
  //   Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_5, dish);
  //   HashMap<String, Rating> response = removeRating(dish);
  //   return Response.ok().entity(response).build();
  // }

  // /**
  //  * Delete a picture for a dish.
  //  *
  //  * @param dish Name of the dish.
  //  * @return JSON encoded list of pictures.
  //  */
  // @DELETE
  // @Path("/dishes/{dish}/pictures")
  // @Produces(MediaType.APPLICATION_JSON)
  // @Consumes(MediaType.APPLICATION_JSON)
  // @RolesAllowed("authenticated")
  // public Response deletePicture(
  //   @PathParam("dish") String dish,
  //   Picture picture
  // )
  //   throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
  //   Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_8, dish);
  //   Map<String, ArrayList<Picture>> response = removePicture(dish, picture);
  //   return Response.ok().entity(response).build();
  // }

  // private HashMap<String, Rating> getRatingsForDish(String dish)
  //   throws EnvelopeOperationFailedException {
  //   try {
  //     Envelope env = getOrCreateRatingsEnvelopeForDish(dish);
  //     return (HashMap<String, Rating>) env.getContent();
  //   } catch (EnvelopeAccessDeniedException e) {
  //     return new HashMap<>();
  //   }
  // }

  // private HashMap<String, ArrayList<Picture>> getPicturesForDish(String dish)
  //   throws EnvelopeOperationFailedException {
  //   try {
  //     Envelope env = getOrCreatePicturesEnvelopeForDish(dish);
  //     return (HashMap<String, ArrayList<Picture>>) env.getContent();
  //   } catch (EnvelopeAccessDeniedException e) {
  //     return new HashMap<>();
  //   }
  // }

  // private HashMap<String, Rating> storeRating(String dish, Rating rating)
  //   throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
  //   UserAgent userAgent = (UserAgent) Context.get().getMainAgent();
  //   String username = userAgent.getLoginName();
  //   rating.author = username;
  //   rating.timestamp = getCurrentTimestamp();
  //   Envelope envelope = getOrCreateRatingsEnvelopeForDish(dish);
  //   HashMap<String, Rating> ratings = (HashMap<String, Rating>) envelope.getContent();
  //   ratings.put(username, rating);
  //   envelope.setContent(ratings);
  //   Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
  //   return ratings;
  // }

  // private HashMap<String, Rating> removeRating(String dish)
  //   throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
  //   UserAgent userAgent = (UserAgent) Context.get().getMainAgent();
  //   String username = userAgent.getLoginName();
  //   Envelope envelope = getOrCreateRatingsEnvelopeForDish(dish);
  //   HashMap<String, Rating> ratings = (HashMap<String, Rating>) envelope.getContent();
  //   ratings.remove(username);
  //   envelope.setContent(ratings);
  //   Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
  //   return ratings;
  // }

  // private Map<String, ArrayList<Picture>> storePicture(
  //   String dish,
  //   Picture picture
  // )
  //   throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
  //   UserAgent userAgent = (UserAgent) Context.get().getMainAgent();
  //   String username = userAgent.getLoginName();
  //   picture.author = username;
  //   Envelope envelope = getOrCreatePicturesEnvelopeForDish(dish);
  //   HashMap<String, ArrayList<Picture>> pictures = (HashMap<String, ArrayList<Picture>>) envelope.getContent();
  //   if (!pictures.containsKey(username)) {
  //     pictures.put(username, new ArrayList<>());
  //   }
  //   pictures.get(username).add(picture);
  //   envelope.setContent(pictures);
  //   Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
  //   return pictures;
  // }

  // private Map<String, ArrayList<Picture>> removePicture(
  //   String dish,
  //   Picture picture
  // )
  //   throws EnvelopeAccessDeniedException, EnvelopeOperationFailedException {
  //   UserAgent userAgent = (UserAgent) Context.get().getMainAgent();
  //   String username = userAgent.getLoginName();
  //   Envelope envelope = getOrCreatePicturesEnvelopeForDish(dish);
  //   HashMap<String, ArrayList<Picture>> pictures = (HashMap<String, ArrayList<Picture>>) envelope.getContent();
  //   pictures.get(username).remove(picture);
  //   envelope.setContent(pictures);
  //   Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
  //   return pictures;
  // }

  // private Envelope getOrCreateRatingsEnvelopeForDish(String dish)
  //   throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
  //   String envelopeName = RATINGS_ENVELOPE_PREFIX + dish;
  //   return getOrCreateEnvelope(envelopeName, new HashMap<String, Rating>());
  // }

  // private Envelope getOrCreatePicturesEnvelopeForDish(String dish)
  //   throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
  //   String envelopeName = PICTURES_ENVELOPE_PREFIX + dish;
  //   return getOrCreateEnvelope(
  //     envelopeName,
  //     new HashMap<String, ArrayList<Picture>>()
  //   );
  // }

  // private Envelope getOrCreateDishIndexEnvelope()
  //   throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
  //   return getOrCreateEnvelope(DISH_INDEX_ENVELOPE_NAME, new HashSet<>());
  // }

  // private Envelope getOrCreateEnvelope(
  //   String name,
  //   Serializable defaultContent
  // )
  //   throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
  //   try {
  //     return Context.get().requestEnvelope(name);
  //   } catch (EnvelopeNotFoundException e) {
  //     Envelope envelope = Context
  //       .get()
  //       .createEnvelope(name, Context.get().getServiceAgent());
  //     envelope.setContent(defaultContent);
  //     envelope.setPublic();
  //     Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
  //     return envelope;
  //   }
  // }

  // private String getCurrentTimestamp() {
  //   Date date = new Date(System.currentTimeMillis());
  //   SimpleDateFormat sdf;
  //   sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
  //   sdf.setTimeZone(TimeZone.getTimeZone("CET"));
  //   return sdf.format(date);
  // }
}
