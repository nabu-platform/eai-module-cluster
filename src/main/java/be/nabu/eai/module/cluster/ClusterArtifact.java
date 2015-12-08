package be.nabu.eai.module.cluster;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.developer.ServerConnection;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ResourceContainer;

public class ClusterArtifact extends JAXBArtifact<ClusterConfiguration> {

	private ResourceRepository repository;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private ServerConnection connection;
	
	public ClusterArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "cluster.xml", ClusterConfiguration.class);
	}

	public boolean isLoaded() {
		return repository != null;
	}
	
	public void reload() {
		repository = null;
	}
	
	public ResourceRepository getRepository() {
		URI mainURI = ResourceUtils.getURI(EAIResourceRepository.getInstance().getRoot().getContainer());
		if (repository == null) {
			synchronized(this) {
				if (repository == null) {
					try {
						if (getConfiguration().getHosts().size() > 0) {
							// we take the first host
							String string = getConfiguration().getHosts().get(0);
							int index = string.indexOf(':');
							connection = new ServerConnection(index < 0 ? string : string.substring(0, index), index < 0 ? 5555 : Integer.parseInt(string.substring(index + 1)));
							URI root = connection.getRepositoryRoot();
							if (mainURI.equals(root)) {
								repository = EAIResourceRepository.getInstance();
							}
							else {
								repository = new RemoteRepository(EAIResourceRepository.getInstance(), (ResourceContainer<?>) ResourceFactory.getInstance().resolve(root, null));
								repository.start();
							}
						}
					}
					catch (Exception e) {
						logger.error("Could not get remote repository", e);
					}
				}
			}
		}
		return repository;
	}
	
}
