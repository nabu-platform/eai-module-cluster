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

	private ResourceRepository clusterRepository;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private ServerConnection connection;
	
	public ClusterArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "cluster.xml", ClusterConfiguration.class);
	}

	public boolean isLoaded() {
		return clusterRepository != null;
	}
	
	public void reload() {
		clusterRepository = null;
	}
	
	public ResourceRepository getClusterRepository() {
		URI mainURI = ResourceUtils.getURI(EAIResourceRepository.getInstance().getRoot().getContainer());
		if (clusterRepository == null) {
			synchronized(this) {
				if (clusterRepository == null) {
					try {
						if (getConfiguration().getHosts().size() > 0) {
							// we take the first host
							String string = getConfiguration().getHosts().get(0);
							int index = string.indexOf(':');
							// TODO: perhaps set keystore & principal?
							connection = new ServerConnection(null, null, index < 0 ? string : string.substring(0, index), index < 0 ? 5555 : Integer.parseInt(string.substring(index + 1)));
							URI root = connection.getRepositoryRoot();
							if (mainURI.equals(root)) {
								clusterRepository = EAIResourceRepository.getInstance();
							}
							else {
								clusterRepository = new RemoteRepository(EAIResourceRepository.getInstance(), (ResourceContainer<?>) ResourceFactory.getInstance().resolve(root, null));
								clusterRepository.start();
							}
						}
					}
					catch (Exception e) {
						logger.error("Could not get remote repository", e);
					}
				}
			}
		}
		return clusterRepository;
	}
	
	public void reloadAll() {
		try {
			if (getConfiguration().getHosts() != null) {
				for (String host : getConfiguration().getHosts()) {
					logger.info("Reloading all on " + host);
					try {
						int index = host.indexOf(':');
						ServerConnection connection = new ServerConnection(null, null, index < 0 ? host : host.substring(0, index), index < 0 ? 5555 : Integer.parseInt(host.substring(index + 1)));
						connection.getRemote().reloadAll();
					}
					catch (Exception e) {
						logger.error("Could not reload all on server: " + host, e);
					}
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public void reload(String id) {
		try {
			if (getConfiguration().getHosts() != null) {
				for (String host : getConfiguration().getHosts()) {
					logger.info("Reloading " + id + " on " + host);
					try {
						int index = host.indexOf(':');
						ServerConnection connection = new ServerConnection(null, null, index < 0 ? host : host.substring(0, index), index < 0 ? 5555 : Integer.parseInt(host.substring(index + 1)));
						connection.getRemote().reload(id);
					}
					catch (Exception e) {
						logger.error("Could not reload '" + id + "' on server: " + host, e);
					}
				}
			}
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
