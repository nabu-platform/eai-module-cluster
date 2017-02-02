package be.nabu.eai.module.cluster;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.cluster.api.MasterSwitcher;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.eai.server.ServerConnection;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.utils.bully.BullyClient;

public class ClusterArtifact extends JAXBArtifact<ClusterConfiguration> {

	private ResourceRepository clusterRepository;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private Map<String, ServerConnection> connections = new HashMap<String, ServerConnection>();
	private Map<String, String> hostNames = new HashMap<String, String>();
	private List<MasterSwitcher> switchers = new ArrayList<MasterSwitcher>();
	
	private BullyClient bullyClient;
	private String master;
	
	public ClusterArtifact(String id, ResourceContainer<?> directory, Repository repository) {
		super(id, directory, repository, "cluster.xml", ClusterConfiguration.class);
	}

	public boolean isLoaded() {
		return clusterRepository != null;
	}
	
	public void reload() {
		clusterRepository = null;
	}
	
	public boolean isSimulation() {
		return getConfig().isSimulate();
	}
	
	public boolean isClusterMember() {
		return bullyClient != null;
	}
	
	public boolean isMaster() {
		return bullyClient != null && bullyClient.isCurrentMaster();
	}

	public String getMaster() {
		return master;
	}

	void setMaster(String master) {
		// only trigger on actual change
		if ((master == null && this.master != null) || (master != null && !master.equals(this.master))) {
			synchronized(switchers) {
				for (MasterSwitcher switcher : switchers) {
					switcher.switchMaster(master, isMaster());
				}
			}
			this.master = master;
		}
	}
	
	public void addSwitcher(MasterSwitcher switcher) {
		if (!switchers.contains(switcher)) {
			synchronized(switchers) {
				if (!switchers.contains(switcher)) {
					switchers.add(switcher);
				}
			}
		}
	}
	
	public void removeSwitcher(MasterSwitcher switcher) {
		if (switchers.contains(switcher)) {
			synchronized(switchers) {
				switchers.remove(switcher);
			}
		}
	}
	
	public ResourceRepository getClusterRepository() {
		URI mainURI = ResourceUtils.getURI(EAIResourceRepository.getInstance().getRoot().getContainer());
		if (clusterRepository == null) {
			synchronized(this) {
				if (clusterRepository == null) {
					try {
						if (isSimulation()) {
							ResourceContainer<?> clusterContainer = getClusterContainer();
							clusterRepository = new RemoteRepository(EAIResourceRepository.getInstance(), clusterContainer);
							// this assumes the current environment has the required modules and the target environment has them as well!
							((RemoteRepository) clusterRepository).setAllowLocalLookup(true);
							((RemoteRepository) clusterRepository).setLocalLookupRegex(getConfig().getLocalLookupRegex());
							clusterRepository.start();
						}
						else if (getConfiguration().getHosts().size() > 0) {
							// we take the first host
							ServerConnection connection = getConnection(getConfiguration().getHosts().get(0));
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

	public ResourceContainer<?> getClusterContainer() {
		try {
			return getConfig().getUri() == null
				? ResourceUtils.mkdirs(getDirectory(), EAIResourceRepository.PRIVATE)
				: ResourceUtils.mkdir(getConfig().getUri(), null);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public ServerConnection getConnection(String host) throws IOException {
		return getConnection(host, true);
	}

	private ServerConnection getConnection(String host, boolean resolveNames) throws IOException {
		if (getConfiguration().getHosts() != null) {
			boolean found = false;
			if (!getConfiguration().getHosts().contains(host)) {
				if (resolveNames) {
					Map<String, String> hostNames = getHostNames();
					for (String potential : hostNames.keySet()) {
						if (hostNames.get(potential).equals(host)) {
							host = potential;
							found = true;
							break;
						}
					}
				}
			}
			else {
				found = true;
			}
			if (found && !connections.containsKey(host)) {
				synchronized(connections) {
					if (!connections.containsKey(host)) {
						int index = host.indexOf(':');
						// TODO: perhaps set keystore & principal?
						ServerConnection connection = new ServerConnection(null, null, index < 0 ? host : host.substring(0, index), index < 0 ? 5555 : Integer.parseInt(host.substring(index + 1)));
						if (getConfig().getConnectionTimeout() != null) {
							connection.setConnectionTimeout(getConfig().getConnectionTimeout());
						}
						if (getConfig().getSocketTimeout() != null) {
							connection.setSocketTimeout(getConfig().getSocketTimeout());
						}
						connections.put(host, connection);
					}
				}
			}
		}
		return connections.get(host);
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
	
	public Map<String, String> getHostNames() {
		if (getConfig().getHosts() != null) {
			if (hostNames.size() != getConfig().getHosts().size()) {
				synchronized(hostNames) {
					if (hostNames.size() != getConfig().getHosts().size()) {
						for (String host : getConfig().getHosts()) {
							if (!hostNames.containsKey(host)) {
								try {
									ServerConnection connection = getConnection(host, false);
									hostNames.put(host, connection.getName());
								}
								catch (Exception e) {
									logger.debug("Could not resolve host name", e);
								}
							}
						}
					}
				}
			}
		}
		return new HashMap<String, String>(hostNames);
	}

	public BullyClient getBullyClient() {
		return bullyClient;
	}

	public void setBullyClient(BullyClient bullyClient) {
		this.bullyClient = bullyClient;
	}

}
