package i5.las2peer.services.mensaService;

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.LocalNodeManager;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.testing.MockAgentFactory;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

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

    /**
     * Called before a test starts.
     * <p>
     * Sets up the node, initializes connector and adds user agent that can be used throughout the test.
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
        node.startService(new ServiceNameVersion(MensaService.class.getName(), "1.0.2"), "a pass");

        // start connector
        connector = new WebConnector(true, 0, false, 0); // port 0 means use system defined port
        logStream = new ByteArrayOutputStream();
        connector.setLogStream(new PrintStream(logStream));
        connector.start(node);
    }

    /**
     * Called after the test has finished. Shuts down the server and prints out the connector log file for reference.
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

            String[] mensas = {"ahornstrasse", "vita", "templergraben", "academica"};
            String[] languages = {"de-de", "en-US", ""};

            for (String language : languages) {
                for (String mensa : mensas) {
                    result = getMensa(client, mensa, language);
                    Assert.assertEquals(200, result.getHttpCode());
                    System.out.println("Result of '" + mensa + "': " + result.getResponse().trim());
                }
                // Mensa not supported:
                result = getMensa(client, "mensaGibtEsNicht", language);
                Assert.assertEquals(404, result.getHttpCode());
                System.out.println("Result of 'mensaGibtEsNicht': " + result.getResponse().trim());
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.toString());
        }
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

            String[] mensas = {"ahornstrasse", "vita", "templergraben", "academica"};
            String[] commands = {"/mensa"};

            for (String command : commands) {
                for (String mensa : mensas) {
                    result = postCommand(client, "de-de", command, mensa);
                    Assert.assertEquals(200, result.getHttpCode());
                    System.out.println("Result of '" + mensa + "': " + result.getResponse().trim());
                }

                result = postCommand(client, "de-de", command, "mensaGibtEsNicht");
                Assert.assertEquals(200, result.getHttpCode());
                System.out.println("Result of 'mensaGibtEsNicht': " + result.getResponse().trim());
            }

        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.toString());
        }
    }

    /**
     * Test to add a picture for a dish.
     */
    @Test
    public void testAddPicture() {
        // given
        final String SOME_DISH = "Wiener Schnitzel";
        final String SOME_IMAGE_DATA = "data:image/png;base64,SOMEFAKEBASE64";
        MiniClient client = getClient();
        // when
        ClientResponse response = postPicture(client, SOME_DISH, SOME_IMAGE_DATA);
        // then
        Assert.assertEquals(200, response.getHttpCode());
    }

    /**
     * Test to add a picture for a dish.
     */
    @Test
    public void testRetrievePictures() throws JSONException {
        // given
        final String SOME_DISH = "Wiener Schnitzel";
        final String SOME_IMAGE_DATA = "data:image/png;base64,SOMEFAKEBASE64";
        MiniClient client = getClient();
        postPicture(client, SOME_DISH, SOME_IMAGE_DATA);
        // when
        ClientResponse response = getPictures(client, SOME_DISH);
        // then
        Assert.assertEquals(200, response.getHttpCode());
        String expectedJSON = String.format(
                "{\"%s\": [{\"image\": \"%s\", \"author\": \"%s\"}]}",
                testAgent.getLoginName(), SOME_IMAGE_DATA, testAgent.getLoginName());
        JSONAssert.assertEquals(expectedJSON, response.getResponse(), true);
    }


    /**
     * Test to add a rating for a dish.
     */
    @Test
    public void testAddRating() {
        // given
        final String SOME_DISH = "Wiener Schnitzel";
        final int STARS = 5;
        final String SOME_MENSA = "vita";
        final String SOME_COMMENT = "My Comment";
        MiniClient client = getClient();
        // when
        ClientResponse response = postRating(client, SOME_DISH, STARS, SOME_MENSA, SOME_COMMENT);
        // then
        Assert.assertEquals(200, response.getHttpCode());
    }

    /**
     * Test to add a rating for a dish.
     */
    @Test
    public void testRetrieveRatings() throws JSONException {
        // given
        final String SOME_DISH = "Wiener Schnitzel";
        final int SOME_STARS = 5;
        final String SOME_MENSA = "vita";
        final String SOME_COMMENT = "My Comment";
        MiniClient client = getClient();
        postRating(client, SOME_DISH, SOME_STARS, SOME_MENSA, SOME_COMMENT);
        // when
        ClientResponse response = getRatings(client, SOME_DISH);
        // then
        Assert.assertEquals(200, response.getHttpCode());
        String expectedJSON = String.format(
                "{\"%s\": {\"stars\": %s,\"comment\": \"%s\",\"mensa\": \"%s\", \"author\": \"%s\"}}",
                testAgent.getLoginName(), SOME_STARS, SOME_COMMENT, SOME_MENSA, testAgent.getLoginName());
        JSONAssert.assertEquals(expectedJSON, response.getResponse(), false);
    }

    private MiniClient getClient() {
        MiniClient client = new MiniClient();
        client.setConnectorEndpoint(connector.getHttpEndpoint());

        client.setLogin(testAgent.getIdentifier(), testPass);
        return client;
    }

    private ClientResponse getMensa(MiniClient client, String mensa, String language) {
        HashMap<String, String> header = new HashMap<String, String>();
        header.put("accept-language", language);
        return client.sendRequest("GET", mainPath + mensa, "", "text/plain", MediaType.TEXT_HTML + ";charset=utf-8",
                header);
    }

    private ClientResponse postCommand(MiniClient client, String language, String command, String value) {
        HashMap<String, String> header = new HashMap<String, String>();
        header.put("accept-language", language);

        String body = "command=" + command + "&text=" + value;
        return client.sendRequest("POST", mainPath + "command", body, "application/x-www-form-urlencoded",
                MediaType.TEXT_HTML + ";charset=utf-8", header);
    }

    private ClientResponse getPictures(MiniClient client, String dish) {
        try {
            dish = URLEncoder.encode(dish, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return client.sendRequest("GET", mainPath + "dishes/" + dish + "/pictures", "",
                MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, new HashMap<>());
    }

    private ClientResponse postPicture(MiniClient client, String dish, String image) {
        try {
            dish = URLEncoder.encode(dish, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String body = String.format("{\"image\": \"%s\", \"author\": null}", image);
        return client.sendRequest("POST", mainPath + "dishes/" + dish + "/pictures", body,
                MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, new HashMap<>());
    }

    private ClientResponse getRatings(MiniClient client, String dish) {
        try {
            dish = URLEncoder.encode(dish, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return client.sendRequest("GET", mainPath + "dishes/" + dish + "/ratings", "",
                MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, new HashMap<>());
    }

    private ClientResponse postRating(MiniClient client, String dish, int stars, String mensa, String comment) {
        try {
            dish = URLEncoder.encode(dish, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String body = String.format("{\"stars\": %s,\"comment\": \"%s\",\"mensa\": \"%s\", \"author\": null, \"timestamp\": null}", stars, comment, mensa);
        return client.sendRequest("POST", mainPath + "dishes/" + dish + "/ratings", body,
                MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON, new HashMap<>());
    }
}
