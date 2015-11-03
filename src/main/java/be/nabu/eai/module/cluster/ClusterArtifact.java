package be.nabu.eai.module.cluster;

import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.api.ResourceContainer;

public class ClusterArtifact extends JAXBArtifact<ClusterConfiguration> {

	public ClusterArtifact(String id, ResourceContainer<?> directory) {
		super(id, directory, "cluster.xml", ClusterConfiguration.class);
	}

}
