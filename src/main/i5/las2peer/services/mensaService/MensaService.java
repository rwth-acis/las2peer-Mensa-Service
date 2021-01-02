package i5.las2peer.services.mensaService;

import i5.las2peer.api.Context;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.security.UserAgent;
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
import java.sql.Statement;
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
    description = "A las2peer Mensa Service for canteens supported by the OpenMensa API (https://openmensa.org/api/v2).",
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

  private static final String OPEN_MENSA_API_ENDPOINT =
    "https://openmensa.org/api/v2";

  private static HashMap<Integer, Date> lastDishUpdate = new HashMap<>();
  private static Date lastMensasUpdate;
  private static HashMap<String, String> ContextInfo = new HashMap<String, String>();
  private final int maxEntries = 20;

  private String databaseName;
  private int databaseTypeInt = 1;
  private SQLDatabaseType databaseType;
  private String databaseHost;
  private int databasePort;
  private String databaseUser;
  private String databasePassword;
  private SQLDatabase database; // The database instance to write to.

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

  public MensaService() {
    super();
    setFieldValues();
    this.databaseType = SQLDatabaseType.getSQLDatabaseType(databaseTypeInt);

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
      Connection con = database.getDataSource().getConnection();
      con.close();
    } catch (SQLException e) {
      e.printStackTrace();
      System.out.println("Failed to connect to Database: " + e.getMessage());
    }
  }

  @Override
  protected void initResources() {
    super.initResources();
    getResourceConfig().register(PrematchingRequestFilter.class);
    getResourceConfig().register(PrematchingResponseFilter.class);
  }

  @Override
  public Map<String, String> getCustomMessageDescriptions() {
    Map<String, String> descriptions = new HashMap<>();
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_1",
      "# Menu Queried for Mensa\n" +
      "## Format\n" +
      "```json\n" +
      "{ msg: \"<id of mensa>\"}\n" +
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
    //no more options to specify the language menu is returned in local language
    // descriptions.put(
    //   "SERVICE_CUSTOM_MESSAGE_2",
    //   "Menu queried for language. Format: Language in lang-country format."
    // );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_3",
      "Ratings queried for dish. Format: id of dish."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_4",
      "Rating added for dish. Format: id of dish."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_5",
      "Rating deleted for dish. Format: review."
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
      "Menu successfully retrieved. Format: id of mensa ."
    );

    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_20",
      "Menu queried for unsupported mensa. Format: Name of unsupported mensa."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_40",
      "Time in ms to process request. Format: jsonString: 'time': Time is ms,'method': request method as string"
    );

    // not relevant
    // descriptions.put(
    //   "SERVICE_CUSTOM_MESSAGE_43",
    //   "update Dish index. Format: Timestamp in ms"
    // );

    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_44",
      "Exception occured. Format: exception message"
    );
    descriptions.put(
      "SERVICE_CUSTOM_ERROR_1",
      "Exception occured. Format: exception message"
    );
    descriptions.put(
      "SERVICE_CUSTOM_ERROR_2",
      "Menu could not be fetched. Format: mensa id"
    );
    descriptions.put("SERVICE_CUSTOM_ERROR_3", "Chatexception occured.");

    return descriptions;
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
    value = "Get the menu of a mensa",
    notes = "The mensa needs to be supported by the OpenMensa API"
  )
  @ApiResponses(
    value = {
      @ApiResponse(
        code = HttpURLConnection.HTTP_OK,
        message = "The call was successfull"
      ),
    }
  )
  public Response getMenu(String body) {
    JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
    JSONObject chatResponse = new JSONObject();
    UserAgent userAgent = (UserAgent) Context.get().getMainAgent();
    System.out.println(userAgent.getLoginName());
    System.out.println(userAgent.getEmail());

    try {
      JSONObject bodyJson = (JSONObject) p.parse(body);
      String channelId = bodyJson.getAsString("channel");
      JSONObject context = getContext(channelId, p);

      String mensa = bodyJson.getAsString("mensa");
      String city = bodyJson.getAsString("city");

      if (mensa == null) {
        mensa = context.getAsString("default_mensa"); // see if user has chosen a default_mensa
        if (mensa == null) throw new ChatException(
          "Please specify the mensa, for which you want to get the menu."
        );
      }
      ResultSet mensas;
      if (city != null) {
        mensas = findMensas(mensa, city);
      } else {
        mensas = findMensas(mensa);
      }
      JSONObject mensaObj = selectMensa(mensas);

      Context
        .get()
        .monitorEvent(
          MonitoringEvent.SERVICE_CUSTOM_MESSAGE_1,
          mensaObj.getAsString("id")
        );

      String menu = createMenuChatResponse(
        mensaObj.getAsString("name"),
        mensaObj.getAsNumber("id").intValue(),
        null
      );

      chatResponse.appendField("text", menu);
      context.put("selected_mensa", mensaObj);
      ContextInfo.put(channelId, context.toJSONString());

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

  /**
   * This method returns the current menu of a canteen. This method only work for mensa academica, ahorn and vita in Aachen
   *
   * @param id    Id of a canteen supported by the OpenMensa API.
   * @param format Format in which the menu should be returned (json or html)
   * @param date Date for which the menu should be queried
   * @return Returns a String containing the menu.
   */
  @GET
  @Path("/{id}")
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
    @PathParam("id") int id,
    @QueryParam("format") @DefaultValue("html") String format,
    @QueryParam("date") @DefaultValue("") String date
  ) {
    JSONArray mensaMenu;
    String returnString;

    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_1,
        String.valueOf(id)
      );

    try {
      mensaMenu = getMensaMenu(id, date);

      if ("html".equals(format)) {
        returnString = convertToHtml(mensaMenu);
      } else {
        returnString = mensaMenu.toString();
      }
    } catch (IOException e) {
      return Response
        .status(Status.NOT_FOUND)
        .entity("Could not get the menu for mensa with id:" + id)
        .build();
    } catch (Exception e) {
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());

      return Response
        .status(Status.INTERNAL_SERVER_ERROR)
        .entity(e.getMessage())
        .build();
    }

    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_10,
        String.valueOf(id)
      );

    String responseContentType;
    switch (format) {
      case "html":
        responseContentType = MediaType.TEXT_HTML + ";charset=utf-8";
        break;
      default:
        responseContentType = MediaType.APPLICATION_JSON;
    }

    return Response.ok().type(responseContentType).entity(returnString).build();
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
  public Response getDishes() {
    JSONArray dishes = new JSONArray();
    JSONObject dish;

    try {
      Connection con = getDatabaseConnection();
      ResultSet res = con
        .prepareStatement("SELECT DISTINCT name,id,category FROM dishes")
        .executeQuery();

      while (res.next()) {
        dish = new JSONObject();
        dish.appendField("name", res.getString("name"));
        dish.appendField("id", res.getInt("id"));
        dish.appendField("category", res.getString("category"));
        dishes.add(dish);
      }
      con.close();

      return Response.ok().entity(dishes).build();
    } catch (Exception e) {
      e.printStackTrace();

      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());

      return Response
        .status(Status.INTERNAL_SERVER_ERROR)
        .entity(e.getMessage())
        .build();
    }
  }

  /**
   * Retrieve all ratings for a dish.
   *
   * @param id id of the dish.
   * @return JSON encoded list of ratings.
   */
  @GET
  @Path("/dishes/{id}/ratings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response getRatings(@PathParam("id") int id) {
    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_3,
        String.valueOf(id)
      );

    try { //get Ratings from distributed storage
      JSONArray reviews = new JSONArray();
      JSONObject review;
      Connection con = getDatabaseConnection();
      PreparedStatement s = con.prepareStatement(
        "SELECT * FROM reviews LEFT JOIN (dishes,mensas) ON (reviews.dishid =dishes.id AND mensas.id=reviews.mensaId) WHERE dishes.id=?"
      );
      s.setInt(1, id);
      ResultSet res = s.executeQuery();
      while (res.next()) {
        review = new JSONObject();
        review.put("id", res.getInt("reviews.id"));
        review.put("mensa", res.getString("mensas.name"));
        review.put("stars", res.getInt("stars"));
        review.put("comment", res.getString("comment"));
        review.put("author", res.getString("author"));
        review.put("timestamp", res.getDate("timestamp").toString());
        reviews.add(review);
      }

      con.close();

      return Response.ok().entity(reviews).build();
    } catch (Exception e) {
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());
      return Response
        .status(Status.INTERNAL_SERVER_ERROR)
        .entity(e.getMessage())
        .build();
    }
  }

  /**
   * Prepares a review for a chatuser. This function should only be called by a bot service
   * @param body JSONString which contains information about the review. In a first phase it should contain the mensa and category of dish. In a second phase the amount of stars should be provided
   * @return Chatresponse
   */
  @POST
  @Path("/prepareReview")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response prepareReview(String body) {
    JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
    JSONObject context; //holds the context between the user and the bot
    JSONObject chatResponse = new JSONObject();

    try {
      JSONObject json = (JSONObject) p.parse(body);
      String channelId = json.getAsString("channel");
      Number stars = json.getAsNumber("stars");
      String mensa = json.getAsString("mensa");
      String category = json.getAsString("category");
      context = getContext(channelId, p);
      String date = null; //currently only review for food of current day. TODO: adjust such that user can add reviews for certain date

      if (stars != null) { //This is the second step, where the user is specifiying how many stars he gives the dish
        int s = stars.intValue();

        if (s < 0 || s > 5) {
          throw new ChatException("Stars must be between 1 and 5");
        }
        context.put("stars", s);
        ContextInfo.put(channelId, context.toJSONString());
        return Response
          .ok()
          .entity(chatResponse.appendField("text", ""))
          .build();
      }

      //The first step is to find out which canteeen the user visited and what meal he ate

      if (category == (null)) {
        throw new ChatException(
          "I could not determine the category of your dish 🙁"
        );
      }

      if (mensa == (null)) {
        JSONObject mensaObj = (JSONObject) context.get("selected_mensa"); //check if a mensa was previously selected (e.g when getting the menu)
        if (mensaObj == null) {
          mensaObj = (JSONObject) context.get("default_mensa"); //check if default mensa has been set //TODO: actually implement this in getMenu
          if (mensaObj == null) {
            throw new ChatException(
              "I could not determine the mensa, you visited 🙁"
            );
          }
        }
        mensa = mensaObj.getAsString("name");
      }
      ResultSet mensas = findMensas(mensa);
      JSONObject mensaObj = selectMensa(mensas);

      JSONObject dish = extractDishFromMenu(
        mensaObj.getAsNumber("id").intValue(),
        category,
        date
      );

      context.put("selected_mensa", mensaObj); //save the mensa obj in context for later lookup on submitReview
      context.put("selected_dish", dish); //save the dish obj in context for later lookup on submitReview
      chatResponse.appendField(
        "text",
        "You ate " +
        dish.getAsString("name") +
        " at " +
        mensaObj.getAsString("name") +
        ".\n Is this correct?"
      );

      ContextInfo.put(channelId, context.toJSONString()); //save context
    } catch (ChatException e) {
      chatResponse.appendField("text", e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();

      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());

      chatResponse.appendField("text", "Sorry, a problem occured 🙁");
    }
    return Response.ok().entity(chatResponse).build();
  }

  /**
   * Function which will submit a rating for a chatuser. This function should only be called by a bot
   * @param body as JSONString should contain an optional reivew comment as msg field
   * @return Chatresponse
   */
  @POST
  @Path("/submitReview")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response submitReview(String body) {
    JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
    JSONObject context;
    JSONObject chatResponse = new JSONObject();

    try {
      JSONObject json = (JSONObject) p.parse(body);
      String channelId = json.getAsString("channel");
      context = getContext(channelId, p);

      String comment = null;
      boolean containsComment =
        !("rejection".equals(json.getAsString("intent")));
      if (containsComment) {
        comment = json.getAsString("msg");
      }
      String author = json.getAsString("email");

      JSONObject dish = (JSONObject) context.get("selected_dish"); // specified when prepareReview was called
      JSONObject mensa = (JSONObject) context.get("selected_mensa"); // specified when prepareReview was called
      Number starsFromContext = context.getAsNumber("stars"); // specified when prepareReview was called

      if (dish == null) {
        throw new ChatException(
          "Sorry, I could not find the dish, you selected earlier, in my records 🙁"
        );
      }

      if (mensa == null) {
        throw new ChatException(
          "Sorry, I could not find the mensa, you selected earlier, in my records 🙁"
        );
      }

      if (starsFromContext == null) {
        throw new ChatException(
          "Sorry, I could not find the stars, you selected earlier, in my records 🙁"
        );
      }

      JSONObject rating = new JSONObject();
      rating.put("author", author);
      rating.put("mensaId", mensa.getAsNumber("id").intValue());
      rating.put("stars", starsFromContext.intValue());
      rating.put("comment", comment);

      Response res = addRating(
        dish.getAsNumber("id").intValue(),
        rating.toJSONString()
      );

      if (res.getStatus() == 200) {
        chatResponse.appendField(
          "text",
          "Alright I saved your review. Thanks for providing your feedback 😊"
        );
      } else {
        throw new Exception(res.getEntity().toString());
      }
    } catch (ChatException e) {
      chatResponse.appendField("text", e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
      chatResponse.appendField("text", "Sorry, a problem occured 🙁");
    }
    return Response.ok().entity(chatResponse).build();
  }

  /**
   * Add a rating for a dish.
   *
   * @param id id of the dish.
   * @param rating rating as JSON string
   * @return JSON encoded list of ratings.
   */
  @POST
  @Path("/dishes/{id}/ratings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("authenticated")
  public Response addRating(@PathParam("id") int id, String rating) {
    JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);
    JSONObject response = new JSONObject();

    try {
      response = (JSONObject) p.parse(rating);
      response.appendField("dishId", id);

      Connection con = getDatabaseConnection();
      PreparedStatement s = con.prepareStatement(
        "SELECT (name) FROM dishes WHERE id=?"
      );
      s.setInt(1, id);
      ResultSet res = s.executeQuery();

      if (!res.next()) {
        return Response.ok().entity("dish not found in db").build();
      }

      String dish = res.getString(1);
      response.appendField("dish", dish);

      String username = response.getAsString("author");
      if (username == null) {
        username = "anonymous";
      }

      response.put("author", username);

      s =
        con.prepareStatement(
          "INSERT INTO reviews (author,mensaId,dishId,timestamp,stars,comment) VALUES (?,?,?,?,?,?)",
          Statement.RETURN_GENERATED_KEYS
        );

      s.setString(1, username);
      s.setInt(2, response.getAsNumber("mensaId").intValue());
      s.setInt(3, id);
      s.setDate(4, new java.sql.Date(System.currentTimeMillis()));
      s.setInt(5, (Integer) response.getAsNumber("stars"));
      s.setString(6, response.getAsString("comment"));
      s.execute();
      ResultSet rs = s.getGeneratedKeys();

      if (rs.next()) {
        response.appendField("id", rs.getInt(1));
        s.close();
        con.close();

        Context
          .get()
          .monitorEvent(
            MonitoringEvent.SERVICE_CUSTOM_MESSAGE_4,
            String.valueOf(id)
          );

        return Response.ok().entity(response).build();
      } else {
        s.close();
        con.close();

        return Response
          .status(Status.INTERNAL_SERVER_ERROR)
          .entity("could not generate new review")
          .build();
      }
    } catch (Exception e) {
      e.printStackTrace();

      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());

      return Response
        .status(Status.INTERNAL_SERVER_ERROR)
        .entity(e.getMessage())
        .build();
    }
  }

  /**
   * Delete a rating for a dish.
   *
   * @param id id of the dish.
   * @return JSON encoded list of ratings.
   */
  @DELETE
  @Path("/dishes/{id}/ratings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("authenticated")
  public Response deleteRating(@PathParam("id") int id) {
    try {
      Connection con = getDatabaseConnection();
      PreparedStatement s = con.prepareStatement(
        "DELETE FROM reviews WHERE id=?"
      );
      s.setInt(1, id);
      s.executeUpdate();
      s.close();
      con.close();
      Context
        .get()
        .monitorEvent(
          MonitoringEvent.SERVICE_CUSTOM_MESSAGE_5,
          String.valueOf(id)
        );

      return Response.ok().build();
    } catch (SQLException e) {
      e.printStackTrace();

      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());

      return Response.ok().entity(e.getMessage()).build();
    }
  }

  /**
   * Retrieve all pictures for a dish.
   *
   * @param id id of the dish.
   * @return JSON encoded list of pictures.
   */
  @GET
  @Path("/dishes/{id}/pictures")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public Response getPictures(@PathParam("id") int id) {
    // Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_6, dish);
    //TODO: adjust monitoring message
    JSONArray pics = new JSONArray();
    try {
      Connection con = getDatabaseConnection();
      PreparedStatement statement = con.prepareStatement(
        "SELECT * from pictures"
      ); //TODO
      con.close();
    } catch (Exception e) {
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());
    }

    return Response.ok().entity(new JSONArray()).build();
  }

  /**
   * Add a picture for a dish.
   *
   * @param id id of the dish.
   * @param picture picture of a dish encoded in base64
   * @return JSON encoded list of pictures.
   */
  @POST
  @Path("/dishes/{id}/pictures")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("authenticated")
  public Response addPicture(@PathParam("id") int id, Picture picture) {
    //Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_7, id);

    return Response.ok().build();
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
      response = (String) getMensa(getMensaId(text), "html", null).getEntity();
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_10, response);
    }
    return Response.ok().entity(response).build();
  }

  /**Gets the menu for the mensa and formats it as a string which can be presented in chat
   * @param name The name of the mensa
   * @param id The id of the mensa for the OpenMensa API (https://doc.openmensa.org/api/v2)
   */
  private String createMenuChatResponse(String name, int id, String date)
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
      JSONArray mensaMenu = getMensaMenu(id, date);
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
   * Looks up all mensas which match a given name
   * @param mensaName name of the mensa
   * @return the set of mensas which match the mensa name
   */
  private ResultSet findMensas(String mensaName) {
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

  private ResultSet findMensas(String mensaName, String city) {
    Connection dbConnection = null;
    PreparedStatement statement = null;
    ResultSet res;
    try {
      dbConnection = getDatabaseConnection();
      statement =
        dbConnection.prepareStatement(
          "SELECT * FROM mensas WHERE name LIKE ? AND city LIKE ?"
        );
      statement.setString(1, "%" + mensaName + "%");
      statement.setString(2, city);
      res = statement.executeQuery();
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Gets the menu for a given mensa
   * @param mensaID id of the mensa in the OpenMensa API
   * @param date Date for which the menu should be queried, needs to be in "yyyy-MM-dd" Date format
   * @return the menu of the mensa for that day, or Monday if the given day is on a weekend
   * @throws IOException if the menu could not be fetched from the openmensa api
   */
  public JSONArray getMensaMenu(int mensaID, String date) throws IOException {
    JSONParser jsonParser = new JSONParser(JSONParser.MODE_PERMISSIVE);
    String urlString = OPEN_MENSA_API_ENDPOINT + "/canteens/";
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Calendar cal = Calendar.getInstance();
    int weekday = cal.get(Calendar.DAY_OF_WEEK);
    JSONArray menu = new JSONArray();
    boolean closed = true; // if all dishes in the returned menu have the name closed then the mensa is closed

    if (date == null || "".equals(date)) { //if date is not provided get current date or mondy if current day is weekend
      if (weekday == 1) { // Sunday
        Date monday = new Date(new Date().getTime() + ONE_DAY_IN_MS);
        date = dateFormat.format(monday);
      } else if (weekday == 7) { // Saturday
        Date sunday = new Date(new Date().getTime() + 2 * ONE_DAY_IN_MS);
        date = dateFormat.format(sunday);
      } else {
        date = dateFormat.format(new Date());
      }
    }

    // urlString += mensaID + "/days/" + date + "/meals";
    urlString += mensaID + "/days/" + "2020-11-30" + "/meals";

    try {
      URL url = new URL(urlString);
      URLConnection con = url.openConnection();
      con.addRequestProperty("Content-type", "application/json");
      menu = (JSONArray) jsonParser.parse(con.getInputStream());

      for (Object object : menu) {
        String dishname = ((JSONObject) object).getAsString("name");
        if (!dishname.contains("geschlossen") && !dishname.contains("closed")) {
          closed = false;
          break;
        }
      }
      if (closed) {
        throw new IOException("Mensa closed");
      }
      Context
        .get()
        .monitorEvent(
          MonitoringEvent.SERVICE_CUSTOM_MESSAGE_10,
          menu.toString()
        );
      saveDishesToIndex(menu, mensaID);
      return menu;
    } catch (ParseException e) {
      e.printStackTrace();
      return null;
    } catch (IOException e) {
      Context
        .get()
        .monitorEvent(
          MonitoringEvent.SERVICE_CUSTOM_ERROR_2,
          String.valueOf(mensaID)
        );
      throw new IOException("Mensa closed");
    }
  }

  /**Formats a given menu into a text string
   * @param  mensaMenu the menu to convert into a string
   */
  private String convertToHtml(JSONArray mensaMenu) {
    String returnString = "";
    JSONArray menus = mensaMenu;

    for (Object o : menus) {
      JSONObject menuItem = (JSONObject) o;
      String type = menuItem.getAsString("category");
      String dish = menuItem.getAsString("name");
      if (!"geschlossen".equals(dish) && !"closed".equals(dish)) {
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
    }
    returnString += "___\n";

    return returnString;
  }

  /**Saves the dishes for a  menu from a given mensa in the datbase
   * @param menu the menu of dishes that should be saver
   */
  private void saveDishesToIndex(JSONArray menu, int mensaId) {
    Date lastUpdate = lastDishUpdate.get((Integer) mensaId);

    if (
      lastUpdate != null &&
      Math.abs(lastUpdate.getTime() - new Date().getTime()) < SIX_HOURS_IN_MS
    ) {
      return;
    }
    System.out.println("Saving dishes to index...");
    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_43,
        System.currentTimeMillis() + ""
      );
    lastDishUpdate.put((Integer) mensaId, new Date());
    try {
      Connection con = getDatabaseConnection();
      menu.forEach(
        menuitem -> {
          try {
            JSONObject json = (JSONObject) menuitem;
            if (
              !json.containsValue("geschlossen") &&
              !json.containsValue("closed")
            ) {
              addDishEntry(json, con, mensaId);
            }
          } catch (SQLException e) {
            e.printStackTrace();
          }
        }
      );
    } catch (Exception e) {
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());
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
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());
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
      "INSERT IGNORE INTO mensas VALUES(?,?,?,?)"
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

  /**
   * function which extracts the mensa from a resultset. If more than one row are provided a Chatexception is thrown
   * @param mensas Resultset containing the mensas as rows
   * @return Object containing the name and id of the mensa
   * @throws ChatException the error message contains a list of mensas in the set
   * @throws SQLException thrown if a db error occured
   */
  private JSONObject selectMensa(ResultSet mensas)
    throws ChatException, SQLException {
    JSONObject mensa = new JSONObject();

    if (!mensas.next()) throw new ChatException(
      "Sorry, I could not find a mensa with that name. 💁"
    );

    String first = mensas.getString("name"); // first entry
    int id = mensas.getInt("id");
    String response = "I found the following mensas: \n1. " + first + "\n";

    int i = 2;
    while (mensas.next() && i < maxEntries) { //at least 2 entries
      response += i + ". " + mensas.getString("name") + "\n";
      i++;
    }
    if (i == 2) {
      mensa.put("name", first);
      mensa.put("id", id);
      return mensa;
    } else if (i == maxEntries) {
      mensas.last();
      int total = mensas.getRow();
      response += "and " + (total - maxEntries) + " more...\n";
      response +=
        "Specify the name of your mensa more clearly, if your mensa is not on the list\n";
    }
    response += "Please specify your mensa.";
    throw new ChatException(response);
  }

  private JSONObject getContext(String channelId, JSONParser p)
    throws ParseException {
    String obj = ContextInfo.get(channelId);
    //  System.out.println("contex for channel " + channelId + ": " + obj);
    if (obj != null) {
      return (JSONObject) p.parse(obj);
    }
    return new JSONObject();
  }

  /**
   * Retrieves an item from a menu
   * @param mensaId id of mensa for which the menu should be fetched
   * @param keyword keyword for the menuitem function will match it to
   * @param date date of the menu
   * @return Dish as json
   * @throws IOException
   * @throws ChatException
   */
  private JSONObject extractDishFromMenu(
    int mensaId,
    String keyword,
    String date
  )
    throws IOException, ChatException {
    JSONArray menu = getMensaMenu(mensaId, date);
    for (Object item : menu) {
      JSONObject obj = (JSONObject) item;

      if (
        obj.getAsString("category").matches("(?i).*" + keyword + ".*") ||
        obj.getAsString("name").matches("(?i).*" + keyword + ".*")
      ) return obj;
    }

    throw new ChatException("Could not find a dish for " + keyword + "💁\n ");
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

  /** Exceptions ,with messages, that should be returned in Chat */
  protected static class ChatException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    protected ChatException(String message) {
      super(message);
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_3, message);
    }
  }

  //   /**
  //  * checks wether a given mensa is supported
  //  * @param mensa name of the mensa
  //  * @return true if the mensa exists in the database
  //  */
  // private boolean isMensaSupported(String mensa) {
  //   try {
  //     ResultSet res = findMensas(mensa);
  //     return res.next(); //true if at least one entry matches the input
  //   } catch (Exception e) {
  //     e.printStackTrace();
  //     return false;
  //   }
  // }
  // private static class Rating implements Serializable {

  //   /**
  //  *
  //  */
  // private static final long serialVersionUID = 1L;
  // public String author;
  //   public int stars;
  //   public String comment;
  //   public int mensaId;
  //   public String timestamp;

  //   @Override
  //   public boolean equals(Object o) {
  //     if (this == o) return true;
  //     if (o == null || getClass() != o.getClass()) return false;
  //     Rating rating = (Rating) o;
  //     return (
  //       stars == rating.stars &&
  //       author.equals(rating.author) &&
  //       Objects.equals(comment, rating.comment) &&
  //       mensaId == rating.mensaId &&
  //       timestamp.equals(rating.timestamp)
  //     );
  //   }

  //   @Override
  //   public int hashCode() {
  //     return Objects.hash(author, stars, comment, mensaId, timestamp);
  //   }
  // }

  private static class Picture implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    public String image;
    public String author;

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
  // //old implementation using envelopes

  // public static int ordinalIndexOf(String str, String substr, int n) {
  //   int pos = -1;
  //   do {
  //     pos = str.indexOf(substr, pos + 1);
  //   } while (n-- > 0 && pos != -1);
  //   return pos;
  // }

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
