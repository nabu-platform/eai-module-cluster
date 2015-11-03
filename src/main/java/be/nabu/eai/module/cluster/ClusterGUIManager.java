package be.nabu.eai.module.cluster;

import java.io.IOException;
import java.util.List;

import be.nabu.eai.developer.MainController;
import be.nabu.eai.developer.managers.base.BaseJAXBGUIManager;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.property.api.Property;
import be.nabu.libs.property.api.Value;

public class ClusterGUIManager extends BaseJAXBGUIManager<ClusterConfiguration, ClusterArtifact> {

	public ClusterGUIManager() {
		super("Cluster", ClusterArtifact.class, new ClusterManager(), ClusterConfiguration.class);
	}

	@Override
	protected List<Property<?>> getCreateProperties() {
		return null;
	}

	@Override
	protected ClusterArtifact newInstance(MainController controller, RepositoryEntry entry, Value<?>... values) throws IOException {
		return new ClusterArtifact(entry.getId(), entry.getContainer());
	}

}
