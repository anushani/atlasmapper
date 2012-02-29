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

package au.gov.aims.atlasmapperserver.layerGenerator;

import au.gov.aims.atlasmapperserver.ClientConfig;
import au.gov.aims.atlasmapperserver.ConfigManager;
import au.gov.aims.atlasmapperserver.Utils;
import au.gov.aims.atlasmapperserver.dataSourceConfig.WMSDataSourceConfig;
import au.gov.aims.atlasmapperserver.layerConfig.LayerStyleConfig;
import au.gov.aims.atlasmapperserver.layerConfig.WMSLayerConfig;
import org.geotools.data.ows.CRSEnvelope;
import org.geotools.data.ows.GetCapabilitiesRequest;
import org.geotools.data.ows.GetCapabilitiesResponse;
import org.geotools.data.ows.Layer;
import org.geotools.data.ows.OperationType;
import org.geotools.data.ows.Request;
import org.geotools.data.ows.Service;
import org.geotools.data.ows.StyleImpl;
import org.geotools.data.ows.WMSCapabilities;
import org.geotools.data.ows.WMSRequest;
import org.geotools.data.wms.WMS1_3_0;
import org.geotools.data.wms.WebMapServer;
import org.geotools.ows.ServiceException;
import org.opengis.util.InternationalString;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractWMSLayerGenerator<L extends WMSLayerConfig, D extends WMSDataSourceConfig> extends AbstractLayerGenerator<L, D> {
	private static final Logger LOGGER = Logger.getLogger(AbstractWMSLayerGenerator.class.getName());

	// Layer config, for each clients
	// Map<String clientId, Map<String layerId, AbstractLayerConfig layer>>
	private Map<String, Map<String, L>> layerConfigsCache = null;
	private WMSCapabilities wmsCapabilities = null;
	private String serviceTitle = null;
	private String wmsVersion = null;
	private URL featureRequestsUrl = null;
	private URL wmsServiceUrl = null;
	private URL extraWmsServiceUrls = null;
	private URL webCacheUrl = null;
	private URL legendUrl = null;

	public AbstractWMSLayerGenerator(String getCapabilitiesURL) throws IOException, ServiceException {
		this.wmsCapabilities = this.getWMSCapabilities(getCapabilitiesURL);
		this.layerConfigsCache = new HashMap<String, Map<String, L>>();

		if (this.wmsCapabilities != null) {
			WMSRequest wmsRequestCapabilities = this.wmsCapabilities.getRequest();
			if (wmsRequestCapabilities != null) {
				this.featureRequestsUrl = this.getOperationUrl(wmsRequestCapabilities.getGetFeatureInfo());
				this.wmsServiceUrl = this.getOperationUrl(wmsRequestCapabilities.getGetMap());
				this.legendUrl = this.getOperationUrl(wmsRequestCapabilities.getGetLegendGraphic());
				this.wmsVersion = this.wmsCapabilities.getVersion();

				// Implemented in the API - Not impemented in AtlasMapper
				//this.stylesUrl = this.getOperationUrl(request.getGetStyles());

				Service service = this.wmsCapabilities.getService();
				if (service != null) {
					this.serviceTitle = service.getTitle();
				}
			}
		}
	}

	protected abstract L createLayerConfig(ConfigManager configManager);

	private WMSCapabilities getWMSCapabilities(String urlStr) throws IOException, ServiceException {
		LOGGER.log(Level.INFO, "Downloading the Capabilities Document {0}", urlStr);

		URL url = new URL(urlStr);

		// TODO Find a nicer way to detect if the URL is a complete URL to a GetCapabilities document
		if (urlStr.startsWith("file://")) {
			GetCapabilitiesRequest req = getCapRequest(url);
			GetCapabilitiesResponse resp = this.issueFileRequest(req);
			return (WMSCapabilities)resp.getCapabilities();

		} else if (urlStr.contains("?")) {
			WebMapServer server = new WebMapServer(url);
			GetCapabilitiesRequest req = getCapRequest(url);
			GetCapabilitiesResponse resp = server.issueRequest(req);
			return (WMSCapabilities)resp.getCapabilities();
		}

		WebMapServer server = new WebMapServer(url);
		return server.getCapabilities();
	}

	/**
	 * WMS Server already has a unique layer ID for each layers. Nothing to do here.
	 * @param layer
	 * @param dataSourceConfig
	 * @return
	 */
	@Override
	protected String getUniqueLayerId(WMSLayerConfig layer, WMSDataSourceConfig dataSourceConfig) {
		return layer.getLayerId();
	}

	@Override
	public Map<String, L> generateLayerConfigs(ClientConfig clientConfig, D dataSourceConfig) {
		Map<String, L> configs = this.layerConfigsCache.get(clientConfig.getClientId());
		if (configs == null) {
			// API: http://docs.geotools.org/latest/javadocs/org/geotools/data/ows/WMSCapabilities.html
			configs = this.getLayersInfoFromCaps(clientConfig, dataSourceConfig);
			this.layerConfigsCache.put(clientConfig.getClientId(), configs);
		}
		return configs;
	}

	private GetCapabilitiesRequest getCapRequest(final URL url) {
		return new WMS1_3_0.GetCapsRequest(url) {
			@Override
			public URL getFinalURL() {
				return url;
			}
		};
	}

	protected GetCapabilitiesResponse issueFileRequest(Request request) throws IOException, ServiceException {
		URL finalURL = request.getFinalURL();
		URLConnection connection = finalURL.openConnection();
		InputStream inputStream = connection.getInputStream();

		String contentType = connection.getContentType();

		return (GetCapabilitiesResponse)request.createResponse(contentType, inputStream);
	}

	private URL getOperationUrl(OperationType op) {
		if (op == null) {
			return null;
		}
		return op.getGet();
	}

	public String getServiceTitle() {
		return serviceTitle;
	}

	public void setServiceTitle(String serviceTitle) {
		this.serviceTitle = serviceTitle;
	}

	public String getWmsVersion() {
		return wmsVersion;
	}

	public void setWmsVersion(String wmsVersion) {
		this.wmsVersion = wmsVersion;
	}

	public URL getFeatureRequestsUrl() {
		return this.featureRequestsUrl;
	}

	public URL getLegendUrl() {
		return this.legendUrl;
	}

	public URL getWmsServiceUrl() {
		return this.wmsServiceUrl;
	}

	public URL getWebCacheUrl() {
		return this.webCacheUrl;
	}

	private Map<String, L> getLayersInfoFromCaps(
			ClientConfig clientConfig,        // Client's specific layers information (base layers list override, etc.)
			D dataSourceConfig // Data source of layers (to link the layer to its data source)
	) {

		// http://docs.geotools.org/stable/javadocs/org/geotools/data/wms/WebMapServer.html
		Layer rootLayer = this.wmsCapabilities.getLayer();

		Map<String, L> layerInfos = new HashMap<String, L>();
		// Can add it in the config if some users think it's usefull to see the root...

		return this._getLayersInfoFromGeoToolRootLayer(layerInfos, rootLayer, new LinkedList<String>(), clientConfig, dataSourceConfig, true);
	}

	@Override
	public D applyOverrides(D dataSourceConfig) {
		D clone = (D) dataSourceConfig.clone();

		if (this.featureRequestsUrl != null && Utils.isBlank(clone.getFeatureRequestsUrl())) {
			clone.setFeatureRequestsUrl(this.featureRequestsUrl.toString());
		}

		// The wmsServiceUrl set in the AbstractDataSourceConfig is the one set in the
		// AtlasMapper server GUI. It may contains the capabilities URL.
		// The GetMap URL found in the capabilities document is always safer.
		if (this.wmsServiceUrl != null) {
			clone.setServiceUrl(this.wmsServiceUrl.toString());
		}

		if (this.extraWmsServiceUrls != null && Utils.isBlank(clone.getExtraWmsServiceUrls())) {
			clone.setExtraWmsServiceUrls(this.extraWmsServiceUrls.toString());
		}

		if (this.webCacheUrl != null && Utils.isBlank(clone.getWebCacheUrl())) {
			clone.setWebCacheUrl(this.webCacheUrl.toString());
		}

		if (this.legendUrl != null && Utils.isBlank(clone.getLegendUrl())) {
			clone.setLegendUrl(this.legendUrl.toString());
		}

		if (Utils.isBlank(this.wmsVersion) && Utils.isBlank(clone.getWmsVersion())) {
			clone.setWmsVersion(this.wmsVersion);
		}

		return clone;
	}

	// Internal recursive function that takes an actual map of layer and add more layers to it.
	// The method signature suggest that the parameter xmlLayers stay unchanged,
	// and a new map containing the layers is returned. If fact, it modify the
	// map receive as parameter. The other way would be inefficient.
	// I.E.
	// The line
	//     xmlLayers = getXmlLayersFromGeoToolRootLayer(xmlLayers, childLayer, childrenWmsPath);
	// give the same result as
	//     getXmlLayersFromGeoToolRootLayer(xmlLayers, childLayer, childrenWmsPath);
	// The first one is just visualy more easy to understand.
	private Map<String, L> _getLayersInfoFromGeoToolRootLayer(
			Map<String, L> layerConfigs,
			Layer layer,
			List<String> wmsPath,
			ClientConfig clientConfig,
			D dataSourceConfig,
			boolean isRoot) {

		if (layer == null) {
			return layerConfigs;
		}

		// GeoTools API documentation is hopeless and the code is build on compile time.
		// There is the most helpfull reference I found so far:
		// http://svn.osgeo.org/geotools/trunk/modules/extension/wms/src/main/java/org/geotools/data/ows/Layer.java
		List<Layer> children = layer.getLayerChildren();

		if (children != null && !children.isEmpty()) {
			// The layer has children, so it is a Container;
			// If it's not the root, it's a part of the WMS Path, represented as a folder in the GUI.
			List<String> childrenWmsPath = new LinkedList<String>(wmsPath);
			if (!isRoot) {
				String newWmsPart = layer.getTitle();
				if (Utils.isBlank(newWmsPart)) {
					newWmsPart = layer.getName();
				}

				if (Utils.isNotBlank(newWmsPart)) {
					childrenWmsPath.add(newWmsPart);
				}
			}

			for (Layer childLayer : children) {
				layerConfigs = _getLayersInfoFromGeoToolRootLayer(layerConfigs, childLayer, childrenWmsPath, clientConfig, dataSourceConfig, false);
			}
		} else {
			// The layer do not have any children, so it is a real layer

			// Create a string representing the wmsPath
			StringBuilder wmsPathBuf = new StringBuilder();
			for (String wmsPathPart : wmsPath) {
				if (Utils.isNotBlank(wmsPathPart)) {
					if (wmsPathBuf.length() > 0) {
						wmsPathBuf.append("/");
					}
					wmsPathBuf.append(wmsPathPart);
				}
			}

			L layerConfig = this.layerToLayerConfig(layer, wmsPathBuf.toString(), clientConfig, dataSourceConfig);
			if (layerConfig != null) {
				layerConfigs.put(layerConfig.getLayerId(), layerConfig);
			}
		}

		return layerConfigs;
	}

	private L layerToLayerConfig(
			Layer layer,
			String wmsPath,
			ClientConfig clientConfig,
			D dataSourceConfig) {

		L layerConfig = this.createLayerConfig(clientConfig.getConfigManager());

		// Link the layer to it's data source
		layerConfig.setDataSourceId(dataSourceConfig.getDataSourceId());

		String layerId = layer.getName();
		if (Utils.isBlank(layerId)) {
			LOGGER.log(Level.WARNING, "The Capabilities Document [{0}] contains layers without name (other than the root layer).", this.serviceTitle);
			return null;
		}

		layerConfig.setLayerId(layerId);

		String title = layer.getTitle();
		if (Utils.isNotBlank(title)) {
			layerConfig.setTitle(title);
		}

		String description = layer.get_abstract();
		if (Utils.isNotBlank(description)) {
			layerConfig.setDescription(description);
		}
		layerConfig.setWmsQueryable(layer.isQueryable());

		if (Utils.isNotBlank(wmsPath)) {
			layerConfig.setWmsPath(wmsPath);
		}

		// Set Baselayer flag if the layer is defined as a base layer in the client OR the client do not define any base layers and the layer is defined as a baselayer is the global config
		if (clientConfig.isOverrideBaseLayers() != null && clientConfig.isOverrideBaseLayers()) {
			if (clientConfig.isBaseLayer(layerId)) {
				layerConfig.setIsBaseLayer(true);
			}
		} else if (dataSourceConfig.isBaseLayer(layerId)) {
			layerConfig.setIsBaseLayer(true);
		}

		List<StyleImpl> styleImpls = layer.getStyles();
		if (styleImpls != null && !styleImpls.isEmpty()) {
			List<LayerStyleConfig> styles = new ArrayList<LayerStyleConfig>(styleImpls.size());
			for (StyleImpl styleImpl : styleImpls) {
				LayerStyleConfig styleConfig = styleToLayerStyleConfig(clientConfig.getConfigManager(), styleImpl);

				if (styleConfig != null) {
					styles.add(styleConfig);
				}
			}
			if (!styles.isEmpty()) {
				layerConfig.setStyles(styles);
			}
		}

		CRSEnvelope boundingBox = layer.getLatLonBoundingBox();
		if (boundingBox != null) {
			double[] boundingBoxArray = {
					boundingBox.getMinX(), boundingBox.getMinY(),
					boundingBox.getMaxX(), boundingBox.getMaxY()
			};
			layerConfig.setLayerBoundingBox(boundingBoxArray);
		}

		this.ensureUniqueLayerId(layerConfig, dataSourceConfig);

		return layerConfig;
	}

	private LayerStyleConfig styleToLayerStyleConfig(ConfigManager configManager, StyleImpl style) {
		String name = style.getName();
		InternationalString intTitle = style.getTitle();
		InternationalString intDescription = style.getAbstract();

		// styleImpl.getLegendURLs() is not implemented yet (gt-wms ver. 2.7.2)
		// See modules/extension/wms/src/main/java/org/geotools/data/wms/xml/WMSComplexTypes.java line 4155
		//List u = styleImpl.getLegendURLs();

		LayerStyleConfig styleConfig = new LayerStyleConfig(configManager);
		styleConfig.setName(name);
		if (intTitle != null) {
			styleConfig.setTitle(intTitle.toString());
		}
		if (intDescription != null) {
			styleConfig.setDescription(intDescription.toString());
		}

		return styleConfig;
	}
}