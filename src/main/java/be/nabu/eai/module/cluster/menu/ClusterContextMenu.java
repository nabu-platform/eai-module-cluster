package be.nabu.eai.module.cluster.menu;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.layout.AnchorPane;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.api.ArtifactDiffer;
import be.nabu.eai.developer.api.ArtifactGUIManager;
import be.nabu.eai.developer.api.EntryContextMenuProvider;
import be.nabu.eai.developer.managers.base.BasePropertyOnlyGUIManager;
import be.nabu.eai.developer.managers.base.JAXBArtifactDiffer;
import be.nabu.eai.developer.util.Confirm;
import be.nabu.eai.developer.util.Confirm.ConfirmType;
import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.api.ResourceRepository;

public class ClusterContextMenu implements EntryContextMenuProvider {

	private static Logger logger = LoggerFactory.getLogger(ClusterContextMenu.class);
	
	@SuppressWarnings("rawtypes")
	@Override
	public Menu getContext(final Entry entry) {
		// if we have a resource-based node, allow for diffing
		if (entry instanceof ResourceEntry && entry.isNode()) {
			ArtifactDiffer differ = getDiffer(entry);
			if (differ != null) {
				Menu menu = new Menu("Compare");
				for (final ClusterArtifact artifact : entry.getRepository().getArtifacts(ClusterArtifact.class)) {
					MenuItem item = new MenuItem(artifact.getId());
					item.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
						@SuppressWarnings("unchecked")
						@Override
						public void handle(ActionEvent arg0) {
							ResourceRepository repository = artifact.getClusterRepository();
							if (repository != null) {
								Entry remoteEntry = repository.getEntry(entry.getId());
								if (remoteEntry == null) {
									Confirm.confirm(ConfirmType.WARNING, entry.getId(), "The item " + entry.getId() + " does not exist in " + artifact.getId(), null);
								}
								else {
									AnchorPane anchorPane = new AnchorPane();
									try {
										if (differ.diff(entry.getNode().getArtifact(), remoteEntry.getNode().getArtifact(), anchorPane)) {
											Tab tab = MainController.getInstance().newTab("Diff: " + entry.getId() + " (" + artifact.getId() + ")");
											tab.setContent(anchorPane);
										}
										else {
											Confirm.confirm(ConfirmType.INFORMATION, entry.getId(), "The item " + entry.getId() + " is in sync on " + artifact.getId(), null);
										}
									}
									catch (Exception e) {
										logger.error("Could not compare " + entry.getId(), e);
									}
								}
							}
						}
					});
					menu.getItems().add(item);
				}
				if (!menu.getItems().isEmpty()) {
					return menu;
				}
			}
		}
		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static ArtifactDiffer<?> getDiffer(Entry entry) {
		List<Class<ArtifactDiffer>> differs = entry.getRepository().getImplementationsFor(ArtifactDiffer.class);
		for (Class<ArtifactDiffer> differ : differs) {
			try {
				ArtifactDiffer instance = differ.newInstance();
				if (instance.getArtifactClass().isAssignableFrom(entry.getNode().getArtifactClass())) {
					return instance;
				}
			}
			catch (Exception e) {
				logger.error("Could not load differ: " + differ, e);
			}
		}
		ArtifactGUIManager<?> guiManager = MainController.getInstance().getGUIManager(entry.getNode().getArtifactClass());
		// if we have a property-based GUI Manager, show the default differ
		if (guiManager != null && BasePropertyOnlyGUIManager.class.isAssignableFrom(guiManager.getClass())) {
			return new JAXBArtifactDiffer((BasePropertyOnlyGUIManager) guiManager);
		}
		return null;
	}

}
