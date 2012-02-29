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

package au.gov.aims.atlasmapperserver.layerConfig;

import au.gov.aims.atlasmapperserver.ConfigManager;
import au.gov.aims.atlasmapperserver.annotation.ConfigField;
import au.gov.aims.atlasmapperserver.dataSourceConfig.ArcGISMapServerDataSourceConfigInterface;

public class ArcGISMapServerLayerConfig extends AbstractLayerConfig implements ArcGISMapServerDataSourceConfigInterface {
	@ConfigField
	private String arcGISPath;

	@ConfigField
	private String ignoredArcGSIPath;

	public ArcGISMapServerLayerConfig(ConfigManager configManager) {
		super(configManager);
	}


	@Override
	public String getIgnoredArcGSIPath() {
		return this.ignoredArcGSIPath;
	}

	@Override
	public void setIgnoredArcGSIPath(String ignoredArcGSIPath) {
		this.ignoredArcGSIPath = ignoredArcGSIPath;
	}

	public String getArcGISPath() {
		return this.arcGISPath;
	}

	public void setArcGISPath(String arcGISPath) {
		this.arcGISPath = arcGISPath;
	}
}