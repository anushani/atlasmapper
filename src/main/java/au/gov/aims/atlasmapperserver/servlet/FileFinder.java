/*
 *  This file is part of AtlasMapper server and clients.
 *
 *  Copyright (C) 2011 Australian Institute of Marine Science
 *
 *  Contact: Gael Lafond <g.lafond@aims.org.au>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package au.gov.aims.atlasmapperserver.servlet;

import au.gov.aims.atlasmapperserver.ClientConfig;
import au.gov.aims.atlasmapperserver.ConfigHelper;
import au.gov.aims.atlasmapperserver.ConfigManager;
import au.gov.aims.atlasmapperserver.Utils;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;

/**
 * Library used to locate configuration files for the AtlasMapper server
 * and the AtlasMapper client.
 *
 * The folder structure:
 * |
 * |--- atlasmapper/ (the Application folder)
 *    |    Private: The path and the name of this folder is set by the "ATLASMAPPER_DATA_DIR" variable.
 *    |        NOTE: If the application is deployed under a different name, "am.war" for example, then the variable
 *    |            name would be "AM_DATA_DIR:
 *    |
 *    |--- cache/
 *    |  |    Private: Folder containing all the files downloaded by the AtlasMapper, while generating the
 *    |  |        data sources.
 *    |  |
 *    |  |--- files/
 *    |  |        Private: Folder containing the raw downloaded files.
 *    |  |
 *    |  |--- cacheMap.json
 *    |           Private: JSON File representing the mapping between the raw downloaded files and their URL.
 *    |               A few more extra info in added to the mapping, such as the HTTP response code, the status
 *    |               of the file, error messages that occurred while downloading / parsing it, etc.
 *    |
 *    |--- clients/
 *    |  |    Private: Folder containing all the generated clients. Each client is public,
 *    |  |        but the folder containing all clients is not.
 *    |  |
 *    |  |--- demo/ (a client folder)
 *    |           Public through the ClientServlet, as long as there is an active client with an ID matching the folder:
 *    |               This is an example of a client ID "demo", accessible at the URL:
 *    |               "http://www.domain.com/atlasmapper/client/demo/".
 *    |               NOTE: There is no "s" in "client" here, since the URL is accessing only one client.
 *    |
 *    |--- datasources/
 *    |        Private: Folder containing all generated data sources state; JSON files used to generate the clients.
 *    |
 *    |--- server.json
 *    |        Private: JSON file containing all the configuration of the data sources & clients.
 *    |
 *    |--- users.json
 *    |        Private: JSON file containing the admin users and their encrypted passwords (md5sum).
 *    |
 *    |--- www/
 *             Public through the ClientServlet: Files placed here are publicly accessible and can be used with
 *                 all clients. It's a good practice to keep public files there since they are not deleted
 *                 when clients folder are deleted.
 *
 * @author glafond
 */
public class FileFinder {
	private static final Logger LOGGER = Logger.getLogger(FileFinder.class.getName());

	// DATA_DIR_PROPERTY can be set in many different ways (same as GeoServer)
	// * Servlet context parameter (web.xml)
	// * Java system property (tomcat/bin/setenv.sh)
	//     Add this line to CATALINA_OPTS variable (replace <path to the config file> with the desired absolute path to the config folder)
	//     -DATLASMAPPER_DATA_DIR=<path to the config file>
	//     NOTE: If the web app is deployed under a different name, the variable name will change subsequently.
	// * Global environment variable (/etc/environment, /etc/profile, /etc/bash.bashrc or the user equivalent)
	// NOTE: Don't forget to restart tomcat after setting this variable.
	private static final String DATA_DIR_PROPERTY = "{WEBAPP-NAME}_DATA_DIR";

	private static final String DATASOURCES_FOLDER = "datasources";

	private static final String CLIENTS_FOLDER = "clients";
	private static final String CLIENT_CONFIG_FOLDER = "config";
	public static final String PUBLIC_FOLDER = "www";
	private static final String ATLASMAPPERCLIENT_FOLDER = "amc";
	private static final String ATLASMAPPERCLIENT_TEMPLATES_FOLDER = "amcTemplates";

	// Must match web.xml, do not starts with a "/" nor ends with a "*" or "/".
	private static final String CLIENT_BASE_URL = "client";
	private static final String CLIENT_WELCOME_PAGE = "index.html";
	private static final String CLIENT_LAYERLIST_PAGE = "list.html";

	private static final String DISK_CACHE_FOLDER = "cache";
	private static final String DISK_CACHE_FILE = "cacheMap.json";

	public static void init(ServletContext context) {
		printDataDirProperty(context);

		boolean create = true;
		File appFolder = FileFinder.getApplicationFolder(context, create);
		FileFinder.getCommonFilesFolder(appFolder, create);
	}

	public static String getDataDirProperty(ServletContext context) {
		String webappName = context.getContextPath().replace("/", "");
		return DATA_DIR_PROPERTY.replace("{WEBAPP-NAME}", webappName.toUpperCase());
	}

	public static File getDiskCacheFolder(File applicationFolder) {
		File folder = new File(applicationFolder, DISK_CACHE_FOLDER);
		folder.mkdirs();

		return folder;
	}

	public static File getDiskCacheFile(File applicationFolder) throws IOException {
		File diskCacheFile = new File(getDiskCacheFolder(applicationFolder), DISK_CACHE_FILE);
		// Create the file is it doesn't exists (check + creation is atomic)
		diskCacheFile.createNewFile();

		return diskCacheFile;
	}

	public static File getPublicFile(ServletContext context, String fileRelativePath) {
		if (context == null || Utils.isBlank(fileRelativePath)) {
			return null;
		}

		// The public folder is not in the Clients base folder
		return new File(
				FileFinder.getApplicationFolder(context, false),
				fileRelativePath);
	}

	public static File getClientFile(ServletContext context, String fileRelativePathWithClientPath) {
		if (context == null || Utils.isBlank(fileRelativePathWithClientPath)) {
			return null;
		}

		return new File(
				FileFinder.getClientsBaseFolder(FileFinder.getApplicationFolder(context, false)),
				fileRelativePathWithClientPath);
	}

	public static String getClientId(String fileRelativePathWithClientPath) {
		if (fileRelativePathWithClientPath == null) {
			return null;
		}

		// The client ID is the first none empty folder in the path
		// 1. Count how many slash are at the beginning of the string (it may be something like: "///clientId/folder/file")
		// NOTE: Tomcat seems to remove redundant slashes, so this loop is just to increase the stability.
		int clientIdStart = 0;
		while (fileRelativePathWithClientPath.charAt(clientIdStart) == '/') {
			clientIdStart++;
		}

		int clientIdEnd = fileRelativePathWithClientPath.indexOf('/', clientIdStart) > 0 ?
				fileRelativePathWithClientPath.indexOf('/', clientIdStart) :
				fileRelativePathWithClientPath.length();

		// 2. Get everything between the last starting slash and the next one ("///clientId/folder/file" => "clientId")
		return fileRelativePathWithClientPath.substring(clientIdStart, clientIdEnd);
	}

	public static ClientConfig getClientConfig(ServletContext context, String clientId) throws IOException, JSONException {
		if (Utils.isBlank(clientId)) {
			return null;
		}

		ConfigManager configManager = ConfigHelper.getConfigManager(context);
		if (configManager == null) {
			return null;
		}

		return configManager.getClientConfig(clientId);
	}

	public static String getAtlasMapperClientURL(ServletContext context, ClientConfig clientConfig) {
		if (clientConfig == null) {
			return null;
		}

		String welcomePage = CLIENT_WELCOME_PAGE;

		// Check if the Welcome file exists on the file system
		File clientFolder = FileFinder.getAtlasMapperClientFolder(FileFinder.getApplicationFolder(context, false), clientConfig);
		if (clientFolder == null || !clientFolder.isDirectory()) {
			// The client has not been generated
			return null;
		}
		String[] content = clientFolder.list();
		if (content == null) {
			return null;
		}
		Arrays.sort(content);
		if (Arrays.binarySearch(content, welcomePage) < 0) {
			// The Welcome file do not exists
			return null;
		}

		String baseUrl = getAtlasMapperClientBaseURL(context, clientConfig).trim();

		String url = null;
		if (Utils.isNotBlank(baseUrl)) {
			if (!baseUrl.endsWith("/")) {
				baseUrl += "/";
			}
			url = baseUrl + welcomePage;
		}

		return url;
	}

	public static String getAtlasMapperLayerListUrl(ServletContext context, ClientConfig clientConfig) {
		if (clientConfig == null) {
			return null;
		}

		// Check if the listing file exists on the file system
		File clientFolder = FileFinder.getAtlasMapperClientFolder(FileFinder.getApplicationFolder(context, false), clientConfig);
		if (clientFolder == null || !clientFolder.isDirectory()) {
			// The client has not been generated
			return null;
		}
		String[] content = clientFolder.list();
		if (content == null) {
			return null;
		}
		Arrays.sort(content);
		if (Arrays.binarySearch(content, CLIENT_LAYERLIST_PAGE) < 0) {
			// The Welcome file do not exists
			return null;
		}

		String baseUrl = getAtlasMapperClientBaseURL(context, clientConfig).trim();

		String url = null;
		if (Utils.isNotBlank(baseUrl)) {
			if (!baseUrl.endsWith("/")) {
				baseUrl += "/";
			}
			url = baseUrl + CLIENT_LAYERLIST_PAGE;
		}

		return url;
	}

	private static String getAtlasMapperClientBaseURL(ServletContext context, ClientConfig clientConfig) {
		if (clientConfig == null) { return null; }
		String clientBaseUrlOverride = clientConfig.getBaseUrl();

		if (Utils.isNotBlank(clientBaseUrlOverride)) {
			return clientBaseUrlOverride;
		}

		String filename = safeFileName(clientConfig.getClientId());
		if (filename == null) {
			return null;
		}

		return context.getContextPath() +
				"/" + CLIENT_BASE_URL +
				"/" + filename;
	}

	public static String getDefaultProxyURL(ServletContext context) {
		return context.getContextPath() + "/proxy";
	}
	public static String getDefaultLayerInfoServiceURL(ServletContext context) {
		return context.getContextPath() + "/public/layersInfo.jsp";
	}
	public static String getDefaultSearchServiceURL(ServletContext context) {
		return context.getContextPath() + "/public/search.jsp";
	}

	public static File getAtlasMapperClientFolder(File applicationFolder, ClientConfig clientConfig) {
		return getAtlasMapperClientFolder(applicationFolder, clientConfig, true);
	}
	public static File getAtlasMapperClientFolder(File applicationFolder, ClientConfig clientConfig, boolean create) {
		return getClientFolder(applicationFolder, clientConfig, create);
	}

	public static File getAtlasMapperClientSourceFolder() throws URISyntaxException {
		URL url = FileFinder.class.getResource("/" + ATLASMAPPERCLIENT_FOLDER);
		if (url == null) { return null; }

		return new File(url.toURI());
	}

	public static File getAtlasMapperClientTemplatesFolder() throws URISyntaxException {
		URL url = FileFinder.class.getResource("/" + ATLASMAPPERCLIENT_TEMPLATES_FOLDER);
		if (url == null) { return null; }

		return new File(url.toURI());
	}

	public static File getAtlasMapperClientConfigFolder(File applicationFolder, ClientConfig clientConfig) {
		return getAtlasMapperClientConfigFolder(applicationFolder, clientConfig, true);
	}
	public static File getAtlasMapperClientConfigFolder(File applicationFolder, ClientConfig clientConfig, boolean create) {
		if (applicationFolder == null || clientConfig == null) {
			return null;
		}
		File clientFolder = getClientFolder(applicationFolder, clientConfig, false);
		if (clientFolder == null) {
			return null;
		}

		File clientConfigFolder = new File(clientFolder, CLIENT_CONFIG_FOLDER);

		if (create && clientConfigFolder != null && !clientConfigFolder.exists()) {
			// Try to create the folder structure, if it doesn't exist
			clientConfigFolder.mkdirs();
		}

		return clientConfigFolder;
	}

	public static File getCommonFilesFolder(File applicationFolder) {
		return FileFinder.getCommonFilesFolder(applicationFolder, true);
	}
	public static File getCommonFilesFolder(File applicationFolder, boolean create) {
		if (applicationFolder == null) {
			return null;
		}

		File publicFolder = new File(applicationFolder, PUBLIC_FOLDER);

		if (create && publicFolder != null && !publicFolder.exists()) {
			// Try to create the folder structure, if it doesn't exist
			publicFolder.mkdirs();
		}

		return publicFolder;
	}

	public static File getClientFolder(File applicationFolder, ClientConfig clientConfig) {
		return FileFinder.getClientFolder(applicationFolder, clientConfig, true);
	}
	public static File getClientFolder(File applicationFolder, ClientConfig clientConfig, boolean create) {
		if (applicationFolder == null || clientConfig == null) {
			return null;
		}

		File clientFolder = null;
		String clientFolderOverrideStr = clientConfig.getGeneratedFileLocation();
		if (Utils.isNotBlank(clientFolderOverrideStr)) {
			clientFolder = new File(clientFolderOverrideStr);
		} else {
			String filename = FileFinder.safeFileName(clientConfig.getClientId());
			if (filename != null) {
				clientFolder = new File(FileFinder.getClientsBaseFolder(applicationFolder), filename);
			}
		}

		if (create && clientFolder != null && !clientFolder.exists()) {
			// Try to create the folder structure, if it doesn't exist
			clientFolder.mkdirs();
		}

		return clientFolder;
	}

	private static File getClientsBaseFolder(File applicationFolder) {
		File clientsFolder = CLIENTS_FOLDER == null ? applicationFolder : new File(applicationFolder, CLIENTS_FOLDER);
		if (!clientsFolder.exists()) {
			clientsFolder.mkdirs();
		}
		return clientsFolder;
	}

	private static File getDataSourcesFolder(File applicationFolder) {
		File dataSourcesFolder = DATASOURCES_FOLDER == null ? applicationFolder : new File(applicationFolder, DATASOURCES_FOLDER);
		if (!dataSourcesFolder.exists()) {
			dataSourcesFolder.mkdirs();
		}
		return dataSourcesFolder;
	}

	public static File getDataSourcesCatalogFile(File applicationFolder, String dataSourceID) {
		File dataSourceFolder = FileFinder.getDataSourcesFolder(applicationFolder);
		return new File(dataSourceFolder, safeFileName(dataSourceID) + ".json");
	}

	public static String safeFileName(String rawFileName) {
		if (Utils.isBlank(rawFileName)) {
			return null;
		}

		// Only allow "-", "_" and alphanumeric.
		// IMPORTANT: Some file system are case-insensitive. It is safer to allow only lower case characters.
		return rawFileName.toLowerCase().replaceAll("\\s", "_").replaceAll("[^a-z0-9-_]", "");
	}

	public static File getApplicationFolder(ServletContext context) {
		return FileFinder.getApplicationFolder(context, true);
	}
	public static File getApplicationFolder(ServletContext context, boolean create) {
		if (context == null) {
			return null;
		}

		File applicationFolder = null;
		String dataDir = FileFinder.getDataDirPropertyValue(context);

		if (dataDir != null) {
			applicationFolder = new File(dataDir);
		}
		if (!Utils.recursiveIsWritable(applicationFolder)) {
			if (applicationFolder != null) {
				LOGGER.log(Level.SEVERE, "The application do not have write access to the folder: [{0}] defined by the property {1}.", new Object[]{
					applicationFolder.getAbsolutePath(),
					getDataDirProperty(context)
				});
			}
		}

		if (create && applicationFolder != null && !applicationFolder.exists()) {
			// Try to create the folder structure, if it doesn't exist
			applicationFolder.mkdirs();
		}

		return applicationFolder;
	}

	// Similar to what GeoServer do
	public static String getDataDirPropertyValue(ServletContext context) {
		if (context == null) { return null; }

		// web.xml
		String dataDir = context.getInitParameter(getDataDirProperty(context));

		// Can be used to set the variable in java, for a Unit Test.
		if (Utils.isBlank(dataDir)) {
			dataDir = System.getProperty(getDataDirProperty(context));
		}

		// tomcat/bin/setenv.sh  or  .bashrc
		if (Utils.isBlank(dataDir)) {
			dataDir = System.getenv(getDataDirProperty(context));
		}

		if (Utils.isNotBlank(dataDir)) {
			return dataDir.trim();
		}
		return null;
	}

	/**
	 * Print the content of the AtlasMapper variable, for logging purpose. This
	 * should be called once, when the application loads, and never get called again,
	 * to avoid noise in the log file.
	 * It is not using a Logger because it must always be present in the log,
	 * whatever the logger settings are.
	 * @param context
	 */
	public static void printDataDirProperty(ServletContext context) {
		String dataDirPropertyValue = FileFinder.getDataDirPropertyValue(context);
		String errorMsg = null;
		if (dataDirPropertyValue == null || dataDirPropertyValue.isEmpty()) {
			errorMsg = FileFinder.getDataDirProperty(context) + " HAS NOT BEEN SET";
		} else {
			File appFolder = new File(dataDirPropertyValue);
			if (!Utils.recursiveIsWritable(appFolder)) {
				if (appFolder.exists()) {
					errorMsg = FileFinder.getDataDirProperty(context) + ": THE FOLDER " + dataDirPropertyValue + " IS NOT WRITABLE";
				} else {
					errorMsg = FileFinder.getDataDirProperty(context) + ": THE FOLDER " + dataDirPropertyValue + " CAN NOT BE CREATED";
				}
			}
		}

		if (errorMsg != null) {
			System.out.println("#######################################");
			System.out.println("# ERROR: " + errorMsg);
			System.out.println("#######################################");
		} else {
			System.out.println("---------------------------------------");
			System.out.println("- " + FileFinder.getDataDirProperty(context) + ": " + dataDirPropertyValue);
			System.out.println("---------------------------------------");
		}
	}
}
