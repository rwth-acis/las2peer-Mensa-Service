package i5.las2peer.services.mensaService;

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.LocalNodeManager;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.testing.MockAgentFactory;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import javax.ws.rs.core.MediaType;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Example Test Class demonstrating a basic JUnit test structure.
 */
public class ServiceTest {

  private static final String testPass = "adamspass";
  private static final String mainPath = "mensa/";
  private static LocalNode node;
  private static WebConnector connector;
  private static ByteArrayOutputStream logStream;
  private static UserAgentImpl testAgent;

  final int SOME_DISH_ID = 5956229;
  final String SOME_DISH = "Bol de salades et crudités assorties"; //makje sure this dish is in the db
  final int STARS = 5;
  final String SOME_MENSA = "academica";
  final String SOME_COMMENT = "My Comment";
  final String SOME_IMAGE_DATA = "data:image/png;base64,SOMEFAKEBASE64";
  final String SOME_DATE = "2020-11-30"; //Please note that the mensa needs to be open on that day

  /**
   * Called before a test starts.
   * <p>
   * Sets up the node, initializes connector and adds user agent that cSyan be used
   * throughout the test.
   *
   * @throws Exception
   */
  @Before
  public void startServer() throws Exception {
    // start node
    node = new LocalNodeManager().newNode();
    node.launch();

    // add agent to node
    testAgent = MockAgentFactory.getAdam();
    testAgent.unlock(testPass); // agents must be unlocked in order to be stored
    node.storeAgent(testAgent);

    // start service
    // during testing, the specified service version does not matter
    node.startService(
      new ServiceNameVersion(MensaService.class.getName(), "1.0.2"),
      "a pass"
    );

    // start connector
    connector = new WebConnector(true, 0, false, 0); // port 0 means use system defined port
    logStream = new ByteArrayOutputStream();
    connector.setLogStream(new PrintStream(logStream));
    connector.start(node);
  }

  /**
   * Called after the test has finished. Shuts down the server and prints out the
   * connector log file for reference.
   *
   * @throws Exception
   */
  @After
  public void shutDownServer() throws Exception {
    if (connector != null) {
      connector.stop();
      connector = null;
    }
    if (node != null) {
      node.shutDown();
      node = null;
    }
    if (logStream != null) {
      System.out.println("Connector-Log:");
      System.out.println("--------------");
      System.out.println(logStream.toString());
      logStream = null;
    }
  }

  /**
   * Test to get menus for some available canteens.
   */
  @Test
  public void testGetMensaMenus() {
    try {
      MiniClient client = getClient();
      ClientResponse result;
      // Try to get the menus

      String[] mensas = { "vita", "academica" };

      for (String mensa : mensas) {
        System.out.println("Trying to fetch menu for mensa " + mensa);
        result = getMensa(client, mensa, "language", SOME_DATE);

        Assert.assertEquals(200, result.getHttpCode());
        // System.out.println(
        //   "Result of '" + mensa + "': " + result.getResponse().trim()
        // );
      }
      //Mensa not supported:
      result = getMensa(client, "mensaGibtEsNicht", "language", SOME_DATE);
      Assert.assertEquals(404, result.getHttpCode());
      // System.out.println(
      //   "Result of 'mensaGibtEsNicht': " + result.getResponse().trim()
      // );
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.toString());
    }
  }

  /**
   * Test get all dishes.
   */
  @Test
  public void testGetDishes() throws JSONException {
    // given
    System.out.println("Adding rating");
    MiniClient client = getClient();
    // when
    ClientResponse res = getMensa(client, SOME_MENSA, "language", SOME_DATE); // fetch menus to ensure that there are dishes in the db
    // then
    Assert.assertEquals(200, res.getHttpCode());
    res = getDishes(client);
    Assert.assertEquals(200, res.getHttpCode());
  }

  /**
   * Test to execute a command.
   */
  @Test
  public void testCommand() {
    try {
      MiniClient client = getClient();
      ClientResponse result;
      // Try to get the menus

      String[] mensas = { "vita", "academica" };
      String[] commands = { "/mensa" };

      for (String command : commands) {
        for (String mensa : mensas) {
          result = postCommand(client, "de-de", command, mensa);
          System.out.println("Post mensa for " + mensa);
          System.out.println(result.getResponse());
          Assert.assertEquals(200, result.getHttpCode());
        }
        // result = postCommand(client, "de-de", command, "mensaGibtEsNicht");
        // Assert.assertEquals(200, result.getHttpCode());
        // System.out.println("Result of 'mensaGibtEsNicht': " +
        // result.getResponse().trim());
      }
    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.toString());
    }
  }

   /**
   * Test emulates an incoming message from the bot manager to get the menu for mensa academica
   */
  @Test
  public void testGetMenuForBot() {
    try {
      MiniClient client = getClient();
      ClientResponse result;

    JSONObject json = new JSONObject();
    json.put("email", "example-email");
    json.put("mensa", "Aachen, Mensa academica");
    json.put("intent","menu");

    
      // Try to get the menus
      result = getBotMenu(client, json.toString());

      Assert.assertEquals(200, result.getHttpCode());

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.toString());
    }
  }

  
   /**
   * Test emulates an incoming message from the bot manager to find mensas in Aachen
   */
  @Test
  public void testGetMensasInCityForBot() {
    try {
      MiniClient client = getClient();
      ClientResponse result;

    JSONObject json = new JSONObject();
    json.put("email", "example-email");
    json.put("city", "Aachen");
    json.put("intent","menu");

    
      // Try to get the menus
    result = getBotMenu(client, json.toString());
    Assert.assertEquals(200, result.getHttpCode());
    JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

    String textResponse =((JSONObject) p.parse(result.getResponse())).getAsString("text");

    Assert.assertEquals(true, textResponse.contains("Mensa Academica"));
    Assert.assertEquals(true, textResponse.contains("Mensa Vita"));

    } catch (Exception e) {
      e.printStackTrace();
      Assert.fail(e.toString());
    }
  }

  // /**
  //  * Test to add a picture for a dish.
  //  */
  // @Test
  // public void testAddPicture() {
  //   // given

  //   MiniClient client = getClient();
  //   // when
  //   ClientResponse response = postPicture(client, SOME_DISH, SOME_IMAGE_DATA);
  //   // then
  //   System.out.println("Post picture: ");
  //   System.out.println(response.getResponse());
  //   Assert.assertEquals(200, response.getHttpCode());
  // }

  // /**
  //  * Test to add a picture for a dish.
  //  */
  // @Test
  // public void testRetrievePictures() throws JSONException {
  //   // given

  //   MiniClient client = getClient();
  //   postPicture(client, SOME_DISH, SOME_IMAGE_DATA);
  //   // when
  //   ClientResponse response = getPictures(client, SOME_DISH);
  //   System.out.println("Retrieve picture: ");
  //   System.out.println(response.getResponse());
  //   // then
  //   Assert.assertEquals(200, response.getHttpCode());
  //   String expectedJSON = String.format(
  //     "{\"%s\": [{\"image\": \"%s\", \"author\": \"%s\"}]}",
  //     testAgent.getLoginName(),
  //     SOME_IMAGE_DATA,
  //     testAgent.getLoginName()
  //   );
  //   JSONAssert.assertEquals(expectedJSON, response.getResponse(), true);
  // }

  // only use this test if removeRating does not work.
  // /**
  //  * Test to add a rating for a dish.
  //  */
  // @Test
  // public void testAddRating() throws JSONException {
  //   // given
  //   System.out.println("Adding rating");
  //   MiniClient client = getClient();
  //   // when
  //   ClientResponse response = postRating(
  //     client,
  //     SOME_DISH_ID,
  //     STARS,
  //     SOME_MENSA,
  //     SOME_COMMENT,
  //     testAgent.getLoginName()
  //   );

  //   // then
  //   String expectedJSON = String.format(
  //     "{\"dish\": \"%s\",\"mensaId\": %s,\"stars\": %s,\"comment\": \"%s\", \"author\": \"%s\"}",
  //     this.SOME_DISH,
  //     String.valueOf(this.getMensaId(this.SOME_MENSA)),
  //     this.STARS,
  //     this.SOME_COMMENT,
  //     testAgent.getLoginName()
  //   );
  //   Assert.assertEquals(200, response.getHttpCode());
  //   JSONAssert.assertEquals(expectedJSON, response.getResponse(), false);
  // }

  // /**
  //  * Test to add and remove a rating for a dish.
  //  */
  // @Test
  // public void testAddAndRemoveRating() throws JSONException {
  //   // given
  //   System.out.println("Adding rating");
  //   MiniClient client = getClient();
  //   // when
  //   ClientResponse response = postRating(
  //     client,
  //     SOME_DISH_ID,
  //     STARS,
  //     SOME_MENSA,
  //     SOME_COMMENT,
  //     testAgent.getLoginName()
  //   );

  //   // then
  //   String expectedJSON = String.format(
  //     "{\"author\": \"%s\",\"comment\": \"%s\",\"mensaId\": %s,\"stars\": %s}",
  //     testAgent.getLoginName(),
  //     this.SOME_COMMENT,
  //     String.valueOf(this.getMensaId(SOME_MENSA)),
  //     this.STARS
  //   );
  //   Assert.assertEquals(200, response.getHttpCode());
  //   JSONAssert.assertEquals(expectedJSON, response.getResponse(), false);

  //   try {
  //     System.out.println(response.getResponse());
  //     JSONTokener obj = new JSONTokener(response.getResponse());

  //     JSONObject json = new JSONObject(obj);
  //     System.out.println(json);
  //     int id = json.getInt("id");
  //     removeRating(client, id);
  //     Assert.assertEquals(200, response.getHttpCode());
  //   } catch (Exception e) {
  //     Assert.fail("parse failed " + e.getMessage());
  //   }
  // }

  // /**
  //  * Test to add a rating for a dish.
  //  */
  // @Test
  // public void testRetrieveRatings() throws JSONException {
  //   System.out.println("Adding rating");
  //   MiniClient client = getClient();
  //   postRating(
  //     client,
  //     SOME_DISH_ID,
  //     STARS,
  //     SOME_MENSA,
  //     SOME_COMMENT,
  //     testAgent.getLoginName()
  //   );
  //   // when
  //   ClientResponse response = getRatings(client, SOME_DISH_ID);
  //   // then
  //   System.out.println("Retrieving ratings");
  //   System.out.println(response.getResponse());
  //   Assert.assertEquals(200, response.getHttpCode());

  //   String expectedJSON = String.format(
  //     "{\"%s\": {\"stars\": %s,\"comment\": \"%s\",\"mensa\": \"%s\", \"author\": \"%s\"}}",
  //     testAgent.getLoginName(),
  //     STARS,
  //     SOME_COMMENT,
  //     getMensaId(SOME_MENSA),
  //     testAgent.getLoginName()
  //   );

  //   try {
  //     JSONArray json = (JSONArray) response.getResponse();
  //     JSONObject res = (JSONObject) json.get(json.length() - 1);

  //     JSONAssert.assertEquals(expectedJSON, res, false);
  //   } catch (Exception e) {
  //     Assert.fail(e.getMessage());
  //   }
  // }
  //TODO partially broken due to conflicts in class casting
  private MiniClient getClient() {
    MiniClient client = new MiniClient();
    client.setConnectorEndpoint(connector.getHttpEndpoint());

    client.setLogin(testAgent.getIdentifier(), testPass);
    return client;
  }

  private ClientResponse getMensa(
    MiniClient client,
    String mensa,
    String language,
    String date
  ) {
    HashMap<String, String> header = new HashMap<String, String>();
    header.put("accept-language", language);
    return client.sendRequest(
      "GET",
      mainPath + getMensaId(mensa) + "?date=" + SOME_DATE,
      "",
      "text/plain",
      MediaType.TEXT_HTML + ";charset=utf-8",
      header
    );
  }

  /**
   * call the bot function to get the menu for a mensa formatted as chat message
   * @param client
   * @param bodyString
   * @return
   */
  private ClientResponse getBotMenu(
    MiniClient client,
    String bodyString
  ){
    return client.sendRequest(
      "POST",
      mainPath + "menu",
      bodyString,
      MediaType.TEXT_HTML,
      MediaType.APPLICATION_JSON,
      new HashMap<String, String>()
    );
  }

  private ClientResponse getDishes(MiniClient client) {
    return client.sendRequest(
      "GET",
      mainPath + "dishes",
      "",
      MediaType.APPLICATION_JSON,
      MediaType.APPLICATION_JSON,
      new HashMap<String, String>()
    );
  }

  private ClientResponse removeRating(MiniClient client, int dishId) {
    return client.sendRequest(
      "GET",
      mainPath + "dishes/" + dishId,
      "",
      MediaType.TEXT_PLAIN,
      MediaType.TEXT_PLAIN,
      new HashMap<>()
    );
  }

  private ClientResponse postCommand(
    MiniClient client,
    String language,
    String command,
    String value
  ) {
    HashMap<String, String> header = new HashMap<String, String>();
    header.put("accept-language", language);

    String body = "command=" + command + "&text=" + value;
    return client.sendRequest(
      "POST",
      mainPath + "command",
      body,
      "application/x-www-form-urlencoded",
      MediaType.TEXT_HTML + ";charset=utf-8",
      header
    );
  }

  private ClientResponse getPictures(MiniClient client, String dish) {
    try {
      dish = URLEncoder.encode(dish, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return client.sendRequest(
      "GET",
      mainPath + "dishes/" + dish + "/pictures",
      "",
      MediaType.APPLICATION_JSON,
      MediaType.APPLICATION_JSON,
      new HashMap<>()
    );
  }

  private ClientResponse postPicture(
    MiniClient client,
    String dish,
    String image
  ) {
    try {
      dish = URLEncoder.encode(dish, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    String body = String.format("{\"image\": \"%s\", \"author\": null}", image);
    return client.sendRequest(
      "POST",
      mainPath + "dishes/" + dish + "/pictures",
      body,
      MediaType.APPLICATION_JSON,
      MediaType.APPLICATION_JSON,
      new HashMap<>()
    );
  }

  private ClientResponse getRatings(MiniClient client, int id) {
    return client.sendRequest(
      "GET",
      mainPath + "dishes/" + id + "/ratings",
      "",
      MediaType.APPLICATION_JSON,
      MediaType.APPLICATION_JSON,
      new HashMap<>()
    );
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

  private ClientResponse postRating(
    MiniClient client,
    int id,
    int stars,
    String mensa,
    String comment,
    String username
  ) {
    String json = String.format(
      "{\"stars\": %s,\"comment\": \"%s\",\"mensaId\": %s, \"author\": \"%s\"}",
      STARS,
      SOME_COMMENT,
      getMensaId(SOME_MENSA),
      testAgent.getLoginName()
    );

    return client.sendRequest(
      "POST",
      mainPath + "dishes/" + id + "/ratings",
      json,
      MediaType.APPLICATION_JSON,
      MediaType.APPLICATION_JSON,
      new HashMap<>()
    );
  }
}
