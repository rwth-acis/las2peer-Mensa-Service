package i5.las2peer.services.mensaService;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.io.IOUtils;

import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import net.minidev.json.JSONObject;

/**
 * las2peer-Mensa-Service
 * 
 * A las2peer service that can display the current menu of a canteen of the RWTH.
 * 
 */

@Api
@SwaggerDefinition(
		info = @Info(
				title = "las2peer Mensa Service",
				version = "1.0",
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
		mensaPage = mensaPage.substring(mensaPage.indexOf("active-headline") + 17);
		mensaPage = mensaPage.substring(0, mensaPage.indexOf("default-headline") - 11);

		// menus
		String menu = mensaPage.substring(mensaPage.indexOf("table") - 1);
		menu = menu.substring(0, menu.indexOf("</table>") + 8);
		JSONObject mensaMenus = new JSONObject();
		while (menu.indexOf("<tr") > 0) {
			int menuTypeEnd = menu.indexOf("</span>");
			String menuType = menu.substring(menu.indexOf("<span class=\"menue-item menue-category\">") + 40,
					menuTypeEnd);
			menu = menu.substring(menuTypeEnd + 1);

			String menuName = menu.substring(menu.indexOf("</span>"), menu.indexOf("<div class=\"nutr-info"));
			menuName = menuName.replaceAll("<sup>[\\sa-zA-Z0-9,]*</sup>", "");
			menuName = menuName.replaceAll("<[^>]*>", "").trim();
			mensaMenus.appendField(menuType, menuName);
			int trIndex = menu.indexOf("</tr>");
			if (trIndex > 0) {
				menu = menu.substring(trIndex + 1);
			} else
				menu = "";
		}
		return mensaMenus;
	}

	/**
	 * This method returns the current menu of a canteen.
	 * 
	 * @param mensa A canteen of the RWTH.
	 * @param language The user's language.
	 * @return Returns a String containing the menu.
	 */
	@GET
	@Path("/{mensa}")
	@Produces(MediaType.TEXT_HTML + ";charset=utf-8")
	@ApiOperation(
			value = "Get the menu of a mensa",
			notes = "The mensa must be supported with the Studierendenwerk")
	@ApiResponses(
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Menu received"),
					@ApiResponse(
							code = HttpURLConnection.HTTP_NOT_FOUND,
							message = "Mensa not supported") })
	public Response getMensa(@PathParam("mensa") String mensa,
			@HeaderParam("accept-language") @DefaultValue("de-de") String language) {
		JSONObject mensaMenu = new JSONObject();
		String returnString = "";
		if (!isMensaSupported(mensa)) {
			return Response.status(Status.NOT_FOUND).entity("Mensa not supported!").build();
		}
		try {
			mensaMenu = getMensaMenu(language, mensa);
			for (String key : mensaMenu.keySet()) {
				if (key.equals("Tellergericht")) {
					returnString += "🍽 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.equals("Vegetarisch")) {
					returnString += "🥗 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.equals("Klassiker")) {
					returnString += "👨🏻‍🍳 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.equals("Empfehlung des Tages")) {
					returnString += "👌🏿👨🏿‍🍳 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.equals("Wok")) {
					returnString += "🥘 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.equals("Ofenkartoffel")) {
					returnString += "🥔 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.equals("Pasta")) {
					returnString += "🍝 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.contains("Pizza")) {
					returnString += "🍕 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.contains("Grill")) {
					returnString += "🥩 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.contains("Burger")) {
					returnString += "🍔 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.contains("Sandwich")) {
					returnString += "🥪 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else if (key.contains("Flammengrill")) {
					returnString += "🔥 " + key + ": " + mensaMenu.getAsString(key) + "\n";
				} else {
					returnString += key + ": " + mensaMenu.getAsString(key) + "\n";
				}
			}
		} catch (IOException e) {
			return Response.status(Status.BAD_REQUEST).build();
		}
		return Response.ok().entity(returnString).build();
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
			value = { @ApiResponse(
					code = HttpURLConnection.HTTP_OK,
					message = "Command executed.") })
	@ApiOperation(
			value = "Perform a command",
			notes = "")
	public Response postTemplate(MultivaluedMap<String, String> form) {
		String cmd = form.getFirst("command");
		String text = form.getFirst("text");
		String response = "";
		if (cmd.equals("/mensa")) {
			if (isMensaSupported(text))
				response = (String) getMensa(text, "de-de").getEntity();
			else
				response = "Mensa not supported";
		}
		return Response.ok().entity(response).build();
	}

	private boolean isMensaSupported(String mensa) {
		if (mensa.equals("vita") || mensa.equals("academica") || mensa.equals("ahornstrasse")
				|| mensa.equals("templergraben")) {
			return true;
		}
		return false;
	}

}
