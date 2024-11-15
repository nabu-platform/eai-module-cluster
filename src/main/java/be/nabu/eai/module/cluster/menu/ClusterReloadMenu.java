/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.cluster.menu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.MenuItem;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.repository.api.Entry;

public class ClusterReloadMenu implements EntryContextMenuProvider {

	private Logger logger = LoggerFactory.getLogger(getClass());
	
	@Override
	public MenuItem getContext(Entry entry) {
		if (entry.isNode() && ClusterArtifact.class.isAssignableFrom(entry.getNode().getArtifactClass())) {
			try {
				final ClusterArtifact artifact = (ClusterArtifact) entry.getNode().getArtifact();
				if (artifact.isLoaded()) {
					MenuItem item = new MenuItem("Reload");
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@Override
						public void handle(ActionEvent arg0) {
							artifact.reload();
						}
					});
					return item;
				}
			}
			catch (Exception e) {
				logger.error("Could not get artifact", e);
			}
		}
		return null;
	}

}
