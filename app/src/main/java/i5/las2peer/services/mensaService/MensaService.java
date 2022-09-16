package i5.las2peer.services.mensaService;

import i5.las2peer.api.Context;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.persistency.Envelope;
import i5.las2peer.api.persistency.EnvelopeAccessDeniedException;
import i5.las2peer.api.persistency.EnvelopeNotFoundException;
import i5.las2peer.api.persistency.EnvelopeOperationFailedException;
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
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
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
  private static HashMap<String, Object> ContextInfo = new HashMap<String, Object>();
  private final int maxEntries = 20;

  private String databaseName="LAS2PEERMON";
  private int databaseTypeInt = 1;
  private SQLDatabaseType databaseType;
  private String databaseHost="127.0.0.1";
  private int databasePort=3306;
  private String databaseUser="root";
  private String databasePassword="root";
  private SQLDatabase database; // The database instance to write to.

  private static final String ENVELOPE_PREFIX = "mensa-";
  // private static final String RATINGS_ENVELOPE_PREFIX =
  //   ENVELOPE_PREFIX + "ratings-";
  private static final String PICTURES_ENVELOPE_PREFIX =
    ENVELOPE_PREFIX + "pictures-";

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
      fetchMensas();
    } catch (SQLException e) {
      // Context
      //   .get()
      // .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_4, e.getMessage()); //this
      // will throw an IllegalStateException because we are not in a service context
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
      "{mensaName:'name of canteen',\n" +
      "mensaId: 'id of canteen',\n" +
      "city:'city of canteen',\n" +
      "day:'day for which the menu was asked'}\n" +
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
      "Rating added for dish. Format: json of rating"
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
      "Menu queried for unsupported mensa. Format: id of unsupported mensa."
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_40",
      "Time in ms to process request. Format: jsonString: 'duration': Time is ms,'url': request method as string"
    );
    descriptions.put(
      "SERVICE_CUSTOM_MESSAGE_41",
      "Time spent in chat performing a task . Format: jsonString: 'time': Time is ms,'task': kind of task as string, email: email of the user as string "
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
    descriptions.put(
      "SERVICE_CUSTOM_ERROR_4",
      "Could not get connection to database"
    );

    return descriptions;
  }

  /**
   * This method is used by the mensa bot. It returns the current menu of supported canteens.
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

    chatResponse.put("text", "");
    chatResponse.put("closeContext", true);

    final long start = System.currentTimeMillis();
    JSONObject monitorEvent41 = new JSONObject();
    JSONObject monitorEvent1 = new JSONObject();

    // System.out.println("Body: "+body);

    try {
      JSONObject bodyJson = (JSONObject) p.parse(body);
      String email = bodyJson.getAsString("email");
      String mensaName = bodyJson.getAsString("mensa");
      String city = bodyJson.getAsString("city");
      String intent = bodyJson.getAsString("intent");
      String day = bodyJson.getAsString("day");

      JSONObject context = getContext(email);

      monitorEvent41.put("email", email);
      monitorEvent41.put("task", "getMenu");

      System.out.println("user: " + email);

      switch (intent) {
        case "quit":
          ContextInfo.remove(email);
          chatResponse.put("text", "Alright. 🙃");
          return Response.ok(chatResponse).build();
        case "rejection":
          chatResponse.put("text", "ok.");
          return Response.ok(chatResponse).build();
        case "confirmation":
          if (context.getAsString("selected_mensa") != null) {
            //user wants to set default mensa
            mensaName =
              ((JSONObject) context.get("selected_mensa")).getAsString("name");
            context.put("default_mensa", mensaName);
            ContextInfo.put(email, context);

            chatResponse.put("text", "Alright. Done! 🎉");
            chatResponse.put("closeContext", true);
          }
          return Response.ok().entity(chatResponse).build();
        case "number_selection":
          if (context.get("currentSelection") instanceof String[]) {
            String[] selection = (String[]) context.get("currentSelection");
            int selected = bodyJson.getAsNumber("number").intValue() - 1;

            System.out.println("Selection: " + selection);
            printArray(selection);

            if (selection.length > selected) {
              mensaName = selection[selected];
            }
            intent = context.getAsString("intent"); //get the previous intent from context
            context.remove("currentSelection");
            ContextInfo.put(email, context);
          }
          break;
        case "menu":
          if (
            mensaName == null && "menu".equals(context.getAsString("intent"))
          ) { //last intent was also menu so we assume that the user now specifies the mensaName
            mensaName = bodyJson.getAsString("msg");
          }
          break;
      }

      if (mensaName == null && city==null) {
        mensaName = context.getAsString("default_mensa");
        if (mensaName == null) {
          throw new ChatException(
            "Please specify the mensa, for which you want to get the menu.\n" +
            "You can also ask me about which mensas are available in your city"
          );
        }
      }

      context = updateContext(bodyJson, context);

      ResultSet mensas = findMensas(mensaName, city);
      JSONObject mensaObj = selectMensa(mensas, context);

      mensaName = mensaObj.getAsString("name");
      city = mensaObj.getAsString("city");

      monitorEvent1.put("mensaName", mensaObj.getAsString("name"));
      monitorEvent1.put("mensaId", mensaObj.getAsString("id"));
      monitorEvent1.put("city", mensaObj.getAsString("city"));
      monitorEvent1.put("day", day);

      System.out.println(
        "Menu queried for mensa " + mensaName + " and city " + city
      );

      Context
        .get()
        .monitorEvent(
          MonitoringEvent.SERVICE_CUSTOM_MESSAGE_1,
          monitorEvent1.toString()
        );

      //TODO: adjust the funftion to get menu for particular day
      String responseString = createMenuChatResponse(
        mensaName,
        mensaObj.getAsNumber("id").intValue(),
        day
      );

      if (
        context.getAsString("default_mensa") == null ||
        !mensaName.equals(context.getAsString("default_mensa"))
      ) {
        //ask to set default mensa when fetching menu for new mensa
        responseString +=
          "\n\n Do you want to set " +
          mensaName +
          " as your default mensa?\nIf you do, you can just write /menu to get the menu for this mensa next time 🙂";
        context.put("selected_mensa", mensaObj);
        ContextInfo.put(email, context);
        chatResponse.put("closeContext", false); // We expect the user to answer to the question.
      }

      chatResponse.put("text", responseString);
      context.put("selected_mensa", mensaObj); //save the selected mensa in context. If user will add a review, this one will be selected if no canteen is provided
      ContextInfo.put(email, context);

      monitorEvent41.put("time", System.currentTimeMillis() - start);
      monitorEvent41.put("mensaName", mensaObj.getAsString("name"));
      monitorEvent41.put("city", mensaObj.getAsString("city"));

      Context
        .get()
        .monitorEvent(
          MonitoringEvent.SERVICE_CUSTOM_MESSAGE_41,
          monitorEvent41.toString()
        );
    } catch (ChatException e) {
      chatResponse.appendField("text", e.getMessage());
      chatResponse.put("closeContext", e.closeContext);
    } catch (Exception e) {
      e.printStackTrace();
      chatResponse.appendField("text", "Sorry, a problem occured 🙁");
    }
    return Response.ok().entity(chatResponse).build();
    /* note that exceptions are sent with status ok we might need to reflect the exception in the status code.
     *I have not tested how the social bot manager handles those though
     */
  }

  /**
   * Lookup all available mensas
   * @param city optionally add this as a query parameter to find mensas in a particular city
   * @return array of mensas
   */
  @GET
  @Path("/find")
  @ApiOperation(
    value = "Get a list of mensas supported by the service. The list is identical to the the one provided by the openmensa api. ",
    notes = "Unlike the OpenMensa Api you can provide a city as query parameter to get all mensas in that city"
  )
  @ApiResponses(
    value = {
      @ApiResponse(code = HttpURLConnection.HTTP_OK, message = ""),
      @ApiResponse(code = 500, message = "SQL exception occured"),
    }
  )
  public Response getSupportedMensas(@QueryParam("city") String city) {
    Response res = null;
    Connection dbConnection = null;
    PreparedStatement statement = null;
    JSONArray mensas = new JSONArray();
    ResultSet rs;
    String query;

    if (city != null) {
      query = "SELECT * FROM mensas WHERE city LIKE ?";
    } else {
      query = "SELECT * FROM mensas";
    }
    try {
      dbConnection = getDatabaseConnection();
      statement = dbConnection.prepareStatement(query);
      if (city != null) {
        statement.setString(1, "%" + city + "%");
      }

      rs = statement.executeQuery();
      while (rs.next()) {
        JSONObject mensa = new JSONObject();
        mensa.put("id", rs.getInt("id"));
        mensa.put("name", rs.getString("name"));
        mensa.put("city", rs.getString("city"));
        mensa.put("address", rs.getString("address"));
        mensas.add(mensa);
      }
      res = Response.ok(mensas).build();
    } catch (SQLException e) {
      e.printStackTrace();
      return Response.status(500).build();
    }

    return res;
  }

  /**
   * This method returns the current menu of a canteen. Will currently not check if mensa is open
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

      String responseContentType;
      switch (format) {
        case "html":
          returnString = convertToHtml(mensaMenu);
          responseContentType = MediaType.TEXT_HTML + ";charset=utf-8";
          break;
        default:
          returnString = mensaMenu.toString();
          responseContentType = MediaType.APPLICATION_JSON;
      }

      return Response
        .ok()
        .type(responseContentType)
        .entity(returnString)
        .build();
    } catch (IOException e) {
      if ("closed".equals(e.getMessage())) {
        return Response
          .status(Status.NOT_FOUND)
          .entity("The canteen is closed on this day")
          .build();
      }
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
    String query = "SELECT DISTINCT name,id,category FROM dishes";
    // String query = "SELECT dishes.name,category,mensas.id  as mensaId ,mensas.name as mensaName FROM dishes JOIN mensas on dishes.mensaId=mensas.id Group by dishes.name;";
    //TODO use second query.
    try {
      Connection con = getDatabaseConnection();
      ResultSet res = con.prepareStatement(query).executeQuery();

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
   * @return JSON encoded list of ratings. rating: 
   *    {"author": 'author of review'
        "stars":'stars given by the author between 1 and 5 stars';
        "comment": 'optional comment'
        "timestamp": 'timestamp of record'
        "category": 'category of dish (klassiker, Vegetarisch,...)'
        "mensaName":'name of the canteen at which the food was consumed'
        "city": 'city in which the canteen is located'}
   */
  @GET
  @Path("/dishes/{id}/ratings")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.TEXT_HTML)
  public Response getRatings(@PathParam("id") int id) {
    Context
      .get()
      .monitorEvent(
        MonitoringEvent.SERVICE_CUSTOM_MESSAGE_3,
        String.valueOf(id)
      );

    try { //get Ratings from distributed storage
      return Response.ok().entity(getRatingsForDish(id)).build();
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

  private float getAverageRating(int dishId) {
    float result;
    try {
      Connection con = getDatabaseConnection();
      PreparedStatement s = con.prepareStatement(
        "SELECT AVG(stars) FROM reviews  WHERE dishId=?"
      );
      s.setInt(1, dishId);
      ResultSet res = s.executeQuery();
      if (res.next()) {
        result = res.getFloat(1);
      } else {
        result = -1;
      }
      con.close();
      return result;
    } catch (SQLException e) {
      e.printStackTrace();
      return -2;
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
    String email = null;
    JSONObject context = null; //holds the context between the user and the bot
    JSONObject chatResponse = new JSONObject();
    JSONObject dish = null;
    JSONObject mensa = null;
    String mensaName = null;

    // System.out.println(body);
    try {
      JSONObject json = (JSONObject) p.parse(body);
      email = json.getAsString("email");
      String intent = json.getAsString("intent");

      if ("quit".equals(intent)) {
        chatResponse.put("text", "Alright. 🙃");
        chatResponse.put("closeContext", true);
        return Response.ok(chatResponse).build();
      }

      context = getContext(email);

      // System.out.println("Context " + context);
      String lastStep = context.getAsString("intent");
      if (
        "number_selection".equals(intent) &&
        context.get("currentSelection") != null &&
        "chooseMensaAndMeal".equals(lastStep)
      ) {
        //the user had specified the mensa not clearly enough and has now chosen a mensa from the suggested list
        int selected = json.getAsNumber("number").intValue() - 1; //selection starts at 1
        String[] selection = (String[]) context.get("currentSelection");

        if (selected < selection.length) {
          mensaName = selection[selected];
        }

        System.out.println("User chose " + mensaName);
        intent = "chooseMensaAndMeal";
        context.put("intent", intent);
        context.remove("currentSelection");
        ContextInfo.put(email, context);
      }

      context = updateContext(json, context);

      mensa = (JSONObject) context.get("selected_mensa"); //mensa object
      dish = (JSONObject) context.get("selected_dish"); //dish object
      if (mensaName == null) mensaName = context.getAsString("mensa"); //name of mensa specified by the user
      String category = context.getAsString("category"); //category specified by the user
      String city = context.getAsString("city"); //city specified by the user
      Number stars = context.getAsNumber("number"); //stars specified by the user

      String date = null; //currently only review for food of current day. TODO: adjust such that user can add reviews for certain date

      if (
        "chooseMensaAndMeal".equals(intent) || "confirmation".equals(intent)
      ) { //The first step is to find out which canteeen the user visited and what meal he ate
        context.putIfAbsent("review_start", System.currentTimeMillis());

        if (mensa == null) {
          if (mensaName == null) {
            mensa = (JSONObject) context.get("selected_mensa"); //check if selected mensa has been set before in getMenu
            if (mensa == null) throw new ChatException(
              "I could not determine the mensa, you visited 🙁. Could you please repeat that? 😇"
            );
            mensaName = mensa.getAsString("name");
          }
          ResultSet mensas = findMensas(mensaName, city);
          mensa = selectMensa(mensas, context);
          context.put("selected_mensa", mensa); //save the mensa obj in context for later lookup on submitReview
        }
        if (dish == null) {
          if (category == null) {
            throw new ChatException(
              "I could not determine the category of your dish 🙁. Could you please repeat that? 😇"
            );
          }
          dish =
            extractDishFromMenu(
              mensa.getAsNumber("id").intValue(),
              category,
              date
            );
          context.put("selected_dish", dish); //save the dish obj in context for later lookup on submitReview
        }

        chatResponse.put(
          "text",
          "You ate " +
          dish.getAsString("name") +
          " at " +
          mensa.getAsString("name") +
          ".\n Is this correct?"
        );
      } else if ("stars".equals(intent) || "number_selection".equals(intent)) { //This is the second step, where the user is specifiying how many stars he gives the dish
        int s = stars.intValue();

        if (s < 1 || s > 5) {
          throw new ChatException("Stars must be between 1 and 5");
        }
        context.put("selected_stars", s);

        chatResponse.put(
          "text",
          "Please comment your rating. If you don't want to add a comment just type \"no\""
        );
      } else if ("rejection".equals(intent)) { //this is the case where the bot recognized the mensa or category wrong
        context.remove("selected_mensa");
        context.remove("selected_dish");
      } else if ("menu".equals(intent)) { //this is the case where the user specifies the mensa
        ResultSet mensas = findMensas(mensaName, city);
        mensa = selectMensa(mensas, context);
        context.put("selected_mensa", mensa); //save the mensa obj in context for later lookup on submitReview
        ContextInfo.put(email, context);
        throw new ChatException(
          "Alright, you went to mensa " +
          mensa.getAsString("name") +
          ". Correct?"
        );
      }
    } catch (ChatException e) {
      chatResponse.appendField("text", e.getMessage());
      chatResponse.put("closeContext", e.closeContext);
    } catch (NumberFormatException e) {
      chatResponse.appendField(
        "text",
        "Please only provide integer numbers... You provided " +
        context.get("stars")
      );
      chatResponse.put("closeContext", false);
    } catch (Exception e) {
      e.printStackTrace();

      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());

      chatResponse.appendField("text", "Sorry, a problem occured 🙁");
    }
    if (email != null && context != null) ContextInfo.put(email, context); //save context
    ContextInfo.put(email, context);
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
    String comment = null;
    JSONObject context = null;
    JSONObject chatResponse = new JSONObject();
    JSONObject event = new JSONObject();
    String email = null;
    event.put("task", "review");

    try {
      JSONObject json = (JSONObject) p.parse(body);
      email = json.getAsString("email");

      context = getContext(email);
      context = updateContext(json, context);

      event.put("email", email);

      boolean containsComment =
        !("rejection".equals(json.getAsString("intent")));
      if (containsComment) {
        comment = json.getAsString("msg");
      }
      String author = json.getAsString("email");

      JSONObject dish = (JSONObject) context.get("selected_dish"); // specified when prepareReview was called
      JSONObject mensa = (JSONObject) context.get("selected_mensa"); // specified when prepareReview was called
      Number starsFromContext = context.getAsNumber("selected_stars"); // specified when prepareReview was called

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
          "Alright your review is saved. Thanks for providing your feedback 😊"
        );
        context.remove("selected_stars");
        context.remove("selected_mensa");
        context.remove("selected_dish");
        Number start = context.getAsNumber("review_start");
        if (start != null) {
          event.put("time", System.currentTimeMillis() - start.longValue());
          Context
            .get()
            .monitorEvent(
              MonitoringEvent.SERVICE_CUSTOM_MESSAGE_41,
              event.toString()
            );
        }
      } else {
        throw new Exception(res.getEntity().toString());
      }
    } catch (ChatException e) {
      chatResponse.appendField("text", e.getMessage());
    } catch (Exception e) {
      e.printStackTrace();
      chatResponse.appendField("text", "Sorry, a problem occured 🙁");
    }
    if (email != null && context != null) {
      ContextInfo.put(email, context);
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
      s.setTimestamp(4, new java.sql.Timestamp(System.currentTimeMillis()));
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
            response.toString()
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
      ); //TODO maybe we should use the file service to add pictures
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
    }
    return Response.ok().entity(response).build();
  }

  /**Gets the menu for the mensa and formats it as a string which can be presented in chat
   * @param name The name of the mensa
   * @param id The id of the mensa for the OpenMensa API (https://doc.openmensa.org/api/v2)
   * @param day The date for which the menu should be returned in weekday format e.g. "Sunday"
   */
  private String createMenuChatResponse(String name, int id, String day)
    throws SQLException, ChatException {
    String MESSAGE_HEAD = "";
    String weekday = new SimpleDateFormat("EEEE").format(new Date()); //e.g. Sunday
    Date date = new Date();

    if (day != null) {
      if ("tomorrow".equals(day)) {
        date = new Date(new Date().getTime() + ONE_DAY_IN_MS); // set date to tomorrow
        weekday = new SimpleDateFormat("EEEE").format(date);
      } else if (!"today".equals(day)) {
        // case if day in  Monday to Sunday
        int today = LocalDate.now().getDayOfWeek().getValue();
        int daysDifference =
          LocalDate.parse(day).getDayOfWeek().getValue() - today; // difference in days between today and provided date
        date = new Date(new Date().getTime() + daysDifference * ONE_DAY_IN_MS); // get the date of the weekday provided by user
        System.out.println(
          "Calculated day: " + weekday + " user provided day: " + day
        );
        weekday = day;
      }
    }

    if (
      "sunday".equals(weekday.toLowerCase()) ||
      "saturday".equals(weekday.toLowerCase())
    ) { //If weekend we try to fetch the menu for following monday (mensas are typically closed on weekends)
      MESSAGE_HEAD +=
        "Please note that canteens are closed on week-ends. This is the menu for Monday\n";
      weekday = "Monday";
    }
    MESSAGE_HEAD +=
      "Here is the menu for mensa " + name + " on " + weekday + " : \n \n";

    try {
      JSONArray mensaMenu = getMensaMenu(
        id,
        new SimpleDateFormat("yyyy-MM-dd").format(date)
      );
      String returnString = convertToHtml(mensaMenu);
      return MESSAGE_HEAD + returnString;
    } catch (IOException e) {
      if ("closed".equals(e.getMessage())) {
        throw new ChatException(
          "Unfortunately, " + name + " is closed on " + weekday + " 😔"
        );
      } else {
        throw new ChatException(
          "The menu for " +
          name +
          " on " +
          weekday +
          " has not been published yet 😔"
        );
      }
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
      statement.setString(1, "%" + mensaName.trim() + "%");
      res = statement.executeQuery();
      return res;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  /**
   * Looks up all entries matching a given name and city in the database and returns the result set
   * @param mensaName name of canteen to search for if not provided, all entries matching the city are returned
   * @param city optional city parameter
   * @return entries matching canteen and city
   */
  private ResultSet findMensas(String mensaName, String city) {
    System.out.println("Looking up canteens for " + mensaName + " and " + city);
    if (city == null) return findMensas(mensaName);
    Connection dbConnection = null;
    PreparedStatement statement = null;
    ResultSet res;
    String query;
    if (mensaName != null) {
      query = "SELECT * FROM mensas WHERE city LIKE ? AND name LIKE ?";
    } else {
      query = "SELECT * FROM mensas WHERE city LIKE ?";
    }
    try {
      dbConnection = getDatabaseConnection();
      statement = dbConnection.prepareStatement(query);
      statement.setString(1, "%" + city.trim() + "%");
      if (mensaName != null) {
        statement.setString(2, "%" + mensaName.trim() + "%");
      }

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
   * @return the menu of the mensa for that date. If no date is given, the current day is used. If the given day is on a weekend, the menu for the following monday is returned.
   * @throws IOException The canteen is closed on the given day or the menu could not be fetched from the openmensa api. If the canteen is closed, the exception message will be "closed"
   */
  public JSONArray getMensaMenu(int mensaID, String date) throws IOException {
    JSONParser jsonParser = new JSONParser(JSONParser.MODE_PERMISSIVE);
    String urlString = OPEN_MENSA_API_ENDPOINT + "/canteens/";
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Calendar cal = Calendar.getInstance();
    int weekday = cal.get(Calendar.DAY_OF_WEEK);
    JSONArray menu = new JSONArray();
    boolean closed = true; // if all dishes in the returned menu have the name closed then the mensa is closed

    if (date == null || "".equals(date)) { //if date is not provided get current date or monday if current day is weekend
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

    urlString += mensaID + "/days/" + date + "/meals";
    // urlString += mensaID + "/days/" + "2020-11-30" + "/meals";

    try {
      if (!MensaIsOpen(mensaID, date)) {
        throw new IOException("closed");
      }

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
        throw new IOException("closed");
      }
      Context
        .get()
        .monitorEvent(
          MonitoringEvent.SERVICE_CUSTOM_MESSAGE_10,
          String.valueOf(mensaID)
        );
      saveDishes(menu, mensaID);
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
      throw e;
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
      int dishId = menuItem.getAsNumber("id").intValue();
      float avg = getAverageRating(dishId);

      if (
        !"geschlossen".equals(dish) &&
        !"closed".equals(dish) &&
        !dish.contains("Boisson")
      ) {
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
        if (avg >= 1) {
          returnString +=
            "Average rating: " + String.format("%.2f", avg) + " out of 5 ⭐ \n";
        }
        returnString += "\n";
      }
    }
    returnString += "___\n";

    return returnString;
  }

  /**Saves the dishes for a  menu from a given mensa in the datbase
   * @param menu the menu of dishes that should be saved
   */
  private void saveDishes(JSONArray menu, int mensaId) {
    Date lastUpdate = lastDishUpdate.get((Integer) mensaId);

    if (
      lastUpdate != null &&
      Math.abs(lastUpdate.getTime() - new Date().getTime()) < SIX_HOURS_IN_MS
    ) {
      return;
    }
    System.out.println("Updating dishes...");
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
      con.close();
      System.out.println("Done saving dishes");
    } catch (Exception e) {
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_1, e.getMessage());
    }
  }

  /**
   * updates the mensas in the database
   * only update the mensas once a month. Mensas do not change that often as
   * mentioned on https://doc.openmensa.org/api/v2/canteens/
   */
  protected void fetchMensas() {
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
      dbConnection = database.getDataSource().getConnection();
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
    if (obj.getAsString("name").contains("Boisson")) {
      return 0; //Luxemburgish canteens add drinks to the menu. Dont save those in dishes
    }
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

  /** Returns a connection to the database. Do not use this function outside a service request contextz
   * like in the constructor of the service, because Contex.get() will throw an IllegalStateException
   */
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
  private JSONObject selectMensa(ResultSet mensas, JSONObject context)
    throws ChatException, SQLException {
    JSONObject mensa = new JSONObject();

    if (mensas == null || !mensas.next()) throw new ChatException(
      "Sorry, I could not find a mensa with that name. 💁"
    );
    String[] selection = new String[maxEntries];
    String first = mensas.getString("name"); // first entry

    int id = mensas.getInt("id");
    String city = mensas.getString("city");
    String response = "I found the following mensas: \n";
    try {
      if (MensaIsOpen(id, null)) {
        response += "1. " + first + "\n";
      } else {
        response += "1. " + first + " (closed)\n";
      }
    } catch (Exception e) {
      e.printStackTrace();
      response += "1. " + first + "\n";
    }

    selection[0] = first;
    int i = 2;

    while (mensas.next() && i < maxEntries) { //at least 2 entries
      try {
        if (MensaIsOpen(mensas.getInt("id"), null)) {
          response += i + ". " + mensas.getString("name") + "\n";
        } else {
          response += i + ". " + mensas.getString("name") + " (closed)\n";
        }
      } catch (Exception e) {
        e.printStackTrace();
        response += i + ". " + mensas.getString("name") + "\n";
      }
      selection[i - 1] = mensas.getString("name");
      i++;
    }

    if (i == 2) {
      mensa.put("name", first);
      mensa.put("city", city);
      mensa.put("id", id);
      return mensa;
    } else if (i == maxEntries) {
      mensas.last();
      int total = mensas.getRow();
      System.out.println("Found a total of " + total + " matching mensas");
      if (total > maxEntries) {
        response += "and " + (total - maxEntries) + " more...\n";
        response +=
          "Specify the name of your mensa more clearly, if your mensa is not on the list\n";
      }
    }
    if (i != 2) {
      //save selection in context
      context.put("currentSelection", selection);
      ContextInfo.put(context.getAsString("email"), context);
    }
    response += "Please specify your mensa.";
    throw new ChatException(response, false);
  }

  /**
   * Updates the context of a converstation by overwriting the old context
   * @param json JSONObject containing the new context
   * @param context old context for which the values will be overwritten
   * @return new context
   */
  private JSONObject updateContext(JSONObject json, JSONObject context) {
    if (context == null) context = new JSONObject();
    for (Map.Entry<String, Object> item : json.entrySet()) {
      if (item.getValue() != null) {
        context.put(item.getKey(), item.getValue());
      }
    }

    return context;
  }

  /**
   * Function to determine if input is an actual canteen name. We assume that every canteen contains either mensa restaurant or cafe
   * @param name name to check
   * @return true if it is considered the name of a canteen
   */
  private boolean isMensa(String name) {
    if (name == null) return false;
    return (
      name.toLowerCase().contains("mensa") ||
      name.toLowerCase().contains("restaurant") ||
      name.toLowerCase().contains("cafe")
    );
  }

  private JSONObject getContext(String email) throws ParseException {
    Object obj = ContextInfo.get(email);

    if (obj instanceof JSONObject) {
      JSONObject context = (JSONObject) (obj);

      return context;
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

  private boolean MensaIsOpen(int mensaID, String date)
      throws MalformedURLException, ParseException, IOException {
    JSONParser jsonParser = new JSONParser(JSONParser.MODE_PERMISSIVE);
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    if (date == null) {
      date = dateFormat.format(new Date());
    }
    String urlString = OPEN_MENSA_API_ENDPOINT + "/canteens/" + mensaID + "/days/" + date;

    URL url = new URL(urlString);

    try{
    URLConnection con = url.openConnection();
    con.setConnectTimeout(30000); // timeout after 30 seconds
    con.setReadTimeout(60000); // timeout after 60 seconds
    con.addRequestProperty("Content-type", "application/json");
    JSONObject response = (JSONObject) jsonParser.parse(con.getInputStream());
    return !(Boolean) response.get("closed");}
    catch(SocketTimeoutException e){
      throw new IOException("The OpenMensa API cannot be reached");
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

  private void printArray(String[] selection) {
    for (String string : selection) {
      System.out.print(string + ", ");
    }
    System.out.println("");
  }

  /** Exceptions ,with messages, that should be returned in Chat */
  protected static class ChatException extends Exception {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    boolean closeContext;

    protected ChatException(String message) {
      super(message);
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_3, message);
      this.closeContext = true;
    }

    protected ChatException(String message, boolean closeContext) {
      super(message);
      this.closeContext = closeContext;
      Context
        .get()
        .monitorEvent(MonitoringEvent.SERVICE_CUSTOM_ERROR_3, message);
    }
  }

  /**
   * checks wether a given mensa is supported
   * @param mensa name of the mensa
   * @return true if the mensa exists in the database
   */
  private boolean isMensaSupported(String mensa) {
    try {
      ResultSet res = findMensas(mensa);
      return res.next(); //true if at least one entry matches the input
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  private static class Rating implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    public String author;
    public int stars;
    public String comment;
    public int mensaId;
    public String timestamp;

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

  private JSONArray getRatingsForDish(int id) {
    JSONArray result = new JSONArray();
    try {
      Connection con = getDatabaseConnection();
      PreparedStatement s = con.prepareStatement(
        "SELECT author, stars, comment, timestamp , category, mensas.name, mensas.city FROM reviews JOIN mensas ON mensas.id=reviews.mensaId JOIN dishes ON dishes.id=reviews.dishId WHERE dishes.id=?"
      );
      s.setInt(1, id);
      ResultSet res = s.executeQuery();
      while (res.next()) {
        JSONObject rating = new JSONObject();
        rating.put("author", res.getString(1));
        rating.put("stars", res.getInt(2));
        rating.put("comment", res.getString(3));
        rating.put("timestamp", res.getDate(4));
        rating.put("category", res.getString(5));
        rating.put("mensaName", res.getString(6));
        rating.put("city", res.getString(7));

        result.add(rating);
      }
      con.close();
      return result;
    } catch (SQLException e) {
      e.printStackTrace();
      return null;
    }
  }

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
  //   storeRating(dish, rating);
  //   return Response.ok().entity("not implemented").build();
  // }

  // private boolean storeRating(String dish, Rating rating) {
  //   return false;
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
  //    {
  //   return Response.ok().entity("not implemented").build();
  // }

  /**
   * Delete a picture for a dish.
   *
   * @param dish Name of the dish.
   * @param picture picture to be deleted
   * @return JSON encoded list of pictures.
   * @throws EnvelopeOperationFailedException could not delete envelope
   * @throws EnvelopeAccessDeniedException no access allowed
   */
  @DELETE
  @Path("/dishes/{dish}/pictures")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("authenticated")
  public Response deletePicture(
    @PathParam("dish") String dish,
    Picture picture
  )
    throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
    Context.get().monitorEvent(MonitoringEvent.SERVICE_CUSTOM_MESSAGE_8, dish);
    Map<String, ArrayList<Picture>> response = removePicture(dish, picture);
    return Response.ok().entity(response).build();
  }

  private HashMap<String, ArrayList<Picture>> getPicturesForDish(String dish)
    throws EnvelopeOperationFailedException {
    try {
      Envelope env = getOrCreatePicturesEnvelopeForDish(dish);
      return (HashMap<String, ArrayList<Picture>>) env.getContent();
    } catch (EnvelopeAccessDeniedException e) {
      return new HashMap<>();
    }
  }

  private Map<String, ArrayList<Picture>> storePicture(
    String dish,
    Picture picture
  )
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

  private Map<String, ArrayList<Picture>> removePicture(
    String dish,
    Picture picture
  )
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

  private Envelope getOrCreatePicturesEnvelopeForDish(String dish)
    throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
    String envelopeName = PICTURES_ENVELOPE_PREFIX + dish;
    return getOrCreateEnvelope(
      envelopeName,
      new HashMap<String, ArrayList<Picture>>()
    );
  }

  private Envelope getOrCreateEnvelope(
    String name,
    Serializable defaultContent
  )
    throws EnvelopeOperationFailedException, EnvelopeAccessDeniedException {
    try {
      return Context.get().requestEnvelope(name);
    } catch (EnvelopeNotFoundException e) {
      Envelope envelope = Context
        .get()
        .createEnvelope(name, Context.get().getServiceAgent());
      envelope.setContent(defaultContent);
      envelope.setPublic();
      Context.get().storeEnvelope(envelope, Context.get().getServiceAgent());
      return envelope;
    }
  }
  // private String getCurrentTimestamp() {
  //   Date date = new Date(System.currentTimeMillis());
  //   SimpleDateFormat sdf;
  //   sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
  //   sdf.setTimeZone(TimeZone.getTimeZone("CET"));
  //   return sdf.format(date);
  // }
}
