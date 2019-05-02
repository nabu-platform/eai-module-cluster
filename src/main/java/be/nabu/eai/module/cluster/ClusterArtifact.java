package be.nabu.eai.module.cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nabu.misc.cluster.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.cluster.api.MasterSwitcher;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.Repository;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.api.cluster.ClusterMember;
import be.nabu.eai.repository.artifacts.jaxb.JAXBArtifact;
import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.eai.server.ServerConnection;
import be.nabu.libs.artifacts.api.StartableArtifact;
import be.nabu.libs.artifacts.api.StoppableArtifact;
import be.nabu.libs.resources.ResourceFactory;
import be.nabu.libs.resources.ResourceUtils;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.utils.bully.BullyClient;

public class ClusterArtifact extends JAXBArtifact<ClusterConfiguration> implements StartableArtifact, StoppableArtifact, be.nabu.eai.repository.api.cluster.ClusterArtifact {

	private ResourceRepository clusterRepository;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private Map<String, ServerConnection> connections = new HashMap<String, ServerConnection>();
	private Map<String, String> hostNames = new HashMap<String, String>();
	private static List<MasterSwitcher> switchers = new ArrayList<MasterSwitcher>();
	
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
		return getBullyClient() != null;
	}
	
	public boolean isMaster() {
		// if the cluster has no hosts or just the one, you are the master of it
		return getConfig().getHosts() == null || getConfig().getHosts().isEmpty() || getConfig().getHosts().size() == 1 || (getBullyClient() != null && getBullyClient().isCurrentMaster());
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
		if (bullyClient == null && ClusterServerListener.getInstance() != null) {
			bullyClient = ClusterServerListener.getInstance().getBullyClient();
		}
		return bullyClient;
	}

	public void setBullyClient(BullyClient bullyClient) {
		this.bullyClient = bullyClient;
	}

	@Override
	public void start() throws IOException {
		ClusterArtifact ownCluster = Services.getOwnCluster(getRepository().newExecutionContext(SystemPrincipal.ROOT));
		if (ownCluster != null && ownCluster.equals(this) && getConfig().getHosts() != null && getConfig().getHosts().size() > 1) {
			if (ClusterServerListener.getInstance() != null) {
				ClusterServerListener.getInstance().setCluster(this);
			}
		}
	}

	@Override
	public boolean isStarted() {
		return ClusterServerListener.getInstance() != null && equals(ClusterServerListener.getInstance().getCluster());
	}

	@Override
	public void stop() throws IOException {
		if (ClusterServerListener.getInstance() != null && equals(ClusterServerListener.getInstance().getCluster())) {
			ClusterServerListener.getInstance().setCluster(null);
		}
	}

	// forward compatible with new cluster logic
	@Override
	public List<ClusterMember> getMembers() {
		List<ClusterMember> members = new ArrayList<ClusterMember>();
		if (getConfig().getHosts() != null) {
			for (String host : getConfig().getHosts()) {
				// can't resolve port
				int index = host.indexOf(':');
				int port = 80;
				if (index >= 0) {
					port = Integer.parseInt(host.substring(index + 1));
					host = host.substring(0, index);
				}
				try {
					final InetSocketAddress address = new InetSocketAddress(host, port); 
					members.add(new ClusterMember() {
						@Override
						public InetSocketAddress getAddress() {
							return address;
						}
					});
				}
				catch (Exception e) {
					logger.error("Could not list member: " + host, e);
				}
			}
		}
		return members;
	}

}
