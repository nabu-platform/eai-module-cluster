package be.nabu.eai.module.cluster;

import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.managers.base.JAXBArtifactManager;
import be.nabu.libs.resources.api.ResourceContainer;

public class ClusterManager extends JAXBArtifactManager<ClusterConfiguration, ClusterArtifact> {

	public ClusterManager() {
		super(ClusterArtifact.class);
	}

	@Override
	protected ClusterArtifact newInstance(String id, ResourceContainer<?> container, Repository repository) {
		return new ClusterArtifact(id, container, repository);
	}

}
