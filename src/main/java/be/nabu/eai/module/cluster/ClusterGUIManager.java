package be.nabu.eai.module.cluster;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.zip.ZipOutputStream;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.Button;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.developer.managers.util.SimpleProperty;
import be.nabu.eai.developer.managers.util.SimplePropertyUpdater;
import be.nabu.eai.developer.util.EAIDeveloperUtils;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ResourceContainer;

public class ClusterGUIManager extends BaseJAXBGUIManager<ClusterConfiguration, ClusterArtifact> {

	public ClusterGUIManager() {
		super("Cluster", ClusterArtifact.class, new ClusterManager(), ClusterConfiguration.class);
	}

	@Override
	public String getCategory() {
		return "Environments";
	}
	
	@Override
	protected List<Property<?>> getCreateProperties() {
		List<Property<?>> properties = new ArrayList<Property<?>>();
		properties.add(new SimpleProperty<Boolean>("Simulation?", Boolean.class, true));
		properties.add(new SimpleProperty<URI>("Simulation Location URI", URI.class, false));
		return properties;
	}

	@Override
	protected ClusterArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>...values) throws IOException {
		ClusterArtifact clusterArtifact = new ClusterArtifact(entry.getId(), entry.getContainer(), entry.getRepository());
		if (values != null) {
			for (Value<?> value : values) {
				if (value.getValue() != null && value.getProperty().getName().equals("Simulation?")) {
					clusterArtifact.getConfig().setSimulate((Boolean) value.getValue());
				}
				else if (value.getValue() != null && value.getProperty().getName().equals("Simulation Location URI")) {
					clusterArtifact.getConfig().setUri((URI) value.getValue());
				}
			}
		}
		return clusterArtifact;
	}

	@Override
	public Collection<Property<?>> getModifiableProperties(ClusterArtifact instance) {
		List<Property<?>> properties = new ArrayList<Property<?>>(super.getModifiableProperties(instance));
		Iterator<Property<?>> iterator = properties.iterator();
		while (iterator.hasNext()) {
			Property<?> next = iterator.next();
			if (next.getName().equals("simulate") || next.getName().equals("uri")) {
				iterator.remove();
			}
		}
		return properties;
	}

	@Override
	protected void display(ClusterArtifact instance, Pane pane) {
		if (instance.isSimulation()) {
			AnchorPane target = new AnchorPane();
			super.display(instance, target);
			VBox result = new VBox();
			HBox buttons = new HBox();
			Button button = new Button("Download Simulation Environment");
			button.addEventHandler(ActionEvent.ANY, new EventHandler<ActionEvent>() {
				@SuppressWarnings({ "unchecked", "rawtypes" })
				@Override
				public void handle(ActionEvent arg0) {
					ResourceContainer<?> clusterContainer = instance.getClusterContainer();
					if (clusterContainer != null) {
						ByteArrayOutputStream output = new ByteArrayOutputStream();
						ZipOutputStream zip = new ZipOutputStream(output);
						try {
							ResourceUtils.zip(clusterContainer, zip, false);
							zip.close();
							byte [] content = output.toByteArray();
							SimpleProperty<File> fileProperty = new SimpleProperty<File>("File", File.class, true);
							SimplePropertyUpdater updater = new SimplePropertyUpdater(true, new LinkedHashSet(Arrays.asList(fileProperty)));
							EAIDeveloperUtils.buildPopup(MainController.getInstance(), updater, "Download Simulation Environment", new EventHandler<ActionEvent>() {
								@Override
								public void handle(ActionEvent arg0) {
									try {
										File file = updater.getValue("File");
										OutputStream output = new BufferedOutputStream(new FileOutputStream(file));
										try {
											output.write(content);
										}
										finally {
											output.close();
										}
									}
									catch (IOException e) {
										MainController.getInstance().notify(e);
									}
								}
							}, false);
						}
						catch (IOException e) {
							MainController.getInstance().notify(e);
						}
					}
				}
			});
			buttons.getChildren().add(button);
			result.getChildren().addAll(buttons, target);
			pane.getChildren().add(result);
			AnchorPane.setBottomAnchor(result, 0d);
			AnchorPane.setLeftAnchor(result, 0d);
			AnchorPane.setRightAnchor(result, 0d);
			AnchorPane.setTopAnchor(result, 0d);
		}
		else {
			super.display(instance, pane);
		}
	}
	
}
