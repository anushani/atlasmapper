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

package au.gov.aims.atlasmapperserver.dataSourceConfig;

import au.gov.aims.atlasmapperserver.ClientConfig;
import au.gov.aims.atlasmapperserver.ConfigManager;
import au.gov.aims.atlasmapperserver.annotation.ConfigField;
import au.gov.aims.atlasmapperserver.layerGenerator.AbstractLayerGenerator;
import au.gov.aims.atlasmapperserver.layerGenerator.BingLayerGenerator;
import org.json.JSONException;
import org.json.JSONObject;

public class BingDataSourceConfig extends AbstractDataSourceConfig {
	@ConfigField
	private String bingAPIKey;

	public BingDataSourceConfig(ConfigManager configManager) {
		super(configManager);
	}

	@Override
	public AbstractLayerGenerator getLayerGenerator() {
		return new BingLayerGenerator(this);
	}

	public String getBingAPIKey() {
		return this.bingAPIKey;
	}

	public void setBingAPIKey(String bingAPIKey) {
		this.bingAPIKey = bingAPIKey;
	}

	@Override
	// TODO Remove clientConfig parameter!!
	public JSONObject generateDataSource(ClientConfig clientConfig) throws JSONException {
		JSONObject dataSource = super.generateDataSource(clientConfig);

		if (this.getBingAPIKey() != null) {
			dataSource.put("bingAPIKey", this.getBingAPIKey());
		}

		return dataSource;
	}
}