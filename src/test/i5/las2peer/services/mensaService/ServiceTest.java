package i5.las2peer.services.mensaService;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import javax.ws.rs.core.MediaType;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.LocalNodeManager;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.testing.MockAgentFactory;

/**
 * Example Test Class demonstrating a basic JUnit test structure.
 *
 */
public class ServiceTest {

	private static LocalNode node;
	private static WebConnector connector;
	private static ByteArrayOutputStream logStream;

	private static UserAgentImpl testAgent;
	private static final String testPass = "adamspass";

	private static final String mainPath = "mensa/";

	/**
	 * Called before a test starts.
	 * 
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
		node.startService(new ServiceNameVersion(MensaService.class.getName(), "1.0.0"), "a pass");

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
	 * 
	 * Test to get menus for some available canteens.
	 * 
	 */
	@Test
	public void testGetMensaMenus() {
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());

			client.setLogin(testAgent.getIdentifier(), testPass);
			ClientResponse result;
			// Try to get the menus

			String[] mensas = { "ahornstrasse", "vita", "templergraben", "academica" };
			String[] languages = { "de-de", "en-US", "" };

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
	 * 
	 * Test to execute a command.
	 * 
	 */
	@Test
	public void testCommand() {
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());

			client.setLogin(testAgent.getIdentifier(), testPass);
			ClientResponse result;
			// Try to get the menus

			String[] mensas = { "ahornstrasse", "vita", "templergraben", "academica" };
			String[] commands = { "/mensa" };

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

}
