package nabu.misc.cluster;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.server.Server;
import be.nabu.eai.server.ServerConnection;
import be.nabu.libs.services.DefinedServiceResolverFactory;
import be.nabu.libs.services.ListableServiceContext;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.ServiceException;
import be.nabu.libs.services.api.ServiceResult;
import be.nabu.libs.services.api.ServiceRunner;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.mask.MaskedContent;
import be.nabu.utils.bully.BullyQueryOverview;

@WebService
public class Services {
	
	private static Logger logger = LoggerFactory.getLogger(Services.class);
	private ExecutionContext executionContext;
	
	public Services() {
		// auto construct
	}
	
	public Services(ExecutionContext executionContext) {
		this.executionContext = executionContext;
	}
	
	@WebResult(name = "history")
	public BullyQueryOverview getHistory() throws SocketException {
		ClusterArtifact ownCluster = getOwnCluster(executionContext);
		return ownCluster == null || ownCluster.getBullyClient() == null ? null : ownCluster.getBullyClient().getHistory();
	}
	
	@WebResult(name = "clusterId")
	public String getCurrentCluster() {
		try {
			ClusterArtifact ownCluster = getOwnCluster(executionContext);
			return ownCluster == null ? null : ownCluster.getId();
		}
		catch (SocketException e) {
			throw new RuntimeException(e);
		}
	}
	
	@WebResult(name = "master")
	public String getMaster(@WebParam(name = "block") Boolean block) throws SocketException, InterruptedException, ExecutionException {
		ClusterArtifact ownCluster = Services.getOwnCluster(executionContext);
		if (ownCluster == null || ownCluster.getConfig().getHosts() == null || ownCluster.getConfig().getHosts().size() <= 1) {
			return null;
		}
		if (block != null && block) {
			Future<String> master = ownCluster.getBullyClient().getMaster();
			return master.get();
		}
		else {
			return ownCluster.getMaster();
		}
	}
	
	@WebResult(name = "started")
	public Boolean scheduleElections() throws SocketException {
		ClusterArtifact ownCluster = Services.getOwnCluster(executionContext);
		if (ownCluster == null || ownCluster.getConfig().getHosts() == null || ownCluster.getConfig().getHosts().size() <= 1) {
			return false;
		}
		ownCluster.getBullyClient().scheduleElection(true);
		return true;
	}

	public static ClusterArtifact getOwnCluster(ExecutionContext executionContext) throws SocketException {
		int port = ((Server) EAIResourceRepository.getInstance().getServiceRunner()).getPort();
		List<ClusterArtifact> clusters = getClustersFor(getLocalAddresses(), executionContext, port);
		if (clusters.isEmpty()) {
			return null;
		}
		else if (clusters.size() == 1) {
			return clusters.get(0);
		}
		// multiple, we need to distinguish by server name
		else {
			throw new RuntimeException("The server is part of multiple clusters: " + clusters);
//			for (ClusterArtifact cluster : clusters) {
//				for (String host : cluster.getConfig().getHosts()) {
//				}
//				if (cluster.getHostNames().values().contains(EAIResourceRepository.getInstance().getName())) {
//					return cluster;
//				}
//			}
		}
	}

	public static String getOwnHostName(ClusterArtifact cluster) throws SocketException, UnknownHostException {
		Integer port = ((Server) EAIResourceRepository.getInstance().getServiceRunner()).getPort();
		List<String> localAddresses = getLocalAddresses();
		for (String host : cluster.getConfig().getHosts()) {
			if (localAddresses.contains(getAddress(host))) {
				int index = host.indexOf(':');
				if (index < 0) {
					return host;
				}
				else if (host.substring(host.indexOf(':') + 1).equals(port.toString())) {
					return host;
				}
			}
		}
		return null;
	}
	
	public static List<ClusterArtifact> getClustersFor(List<String> localAddresses, ExecutionContext executionContext, Integer port) throws SocketException {
		Collection<ClusterArtifact> artifacts = ((ListableServiceContext) executionContext.getServiceContext()).getArtifacts(ClusterArtifact.class);
		List<ClusterArtifact> clusters = new ArrayList<ClusterArtifact>();
		if (artifacts != null && !artifacts.isEmpty()) {
			for (ClusterArtifact artifact : artifacts) {
				try {
					if (artifact.getConfiguration().getHosts() != null) {
						for (String host : artifact.getConfiguration().getHosts()) {
							if (localAddresses.contains(getAddress(host))) {
								if (port == null || (host.contains(":") && host.substring(host.indexOf(':') + 1).equals(port.toString()))) {
									clusters.add(artifact);
								}
							}
						}
					}
				}
				catch (Exception e) {
					logger.error("Could not load cluster configuration for: " + artifact.getId(), e);
				}
			}
		}
		return clusters;
	}
	
	public static String getAddress(String host) throws UnknownHostException {
		InetAddress inetAddress = InetAddress.getByName(host.replaceAll(":.*", ""));
		if (inetAddress == null) {
			throw new IllegalArgumentException("Unknown host: " + host);
		}
		return inetAddress.getHostAddress();
	}
	
	public static List<String> getLocalAddresses() throws SocketException {
		Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
		List<String> addresses = new ArrayList<String>();
		while(networkInterfaces.hasMoreElements()) {
			NetworkInterface networkInterface = networkInterfaces.nextElement();
			Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
			while(inetAddresses.hasMoreElements()) {
				InetAddress inetAddress = inetAddresses.nextElement();
				addresses.add(inetAddress.getHostAddress());
			}
		}
		return addresses;
	}

	@WebResult(name = "peers")
	public List<String> getPeers() throws IOException {
		return getPeers(getOwnCluster(executionContext));
	}

	public static List<String> getPeers(ClusterArtifact ownCluster) throws SocketException, IOException, UnknownHostException {
		List<String> peers = new ArrayList<String>();
		if (ownCluster != null) {
			Map<String, String> hostNames = ownCluster.getHostNames();
			for (String host : hostNames.keySet()) {
				if (!hostNames.get(host).equals(EAIResourceRepository.getInstance().getName())) {
					peers.add(host);
				}
			}
		}
		return peers;
	}
	
	@WebResult(name = "hosts")
	public List<String> getHosts(@WebParam(name = "clusterId") @NotNull String clusterId) throws IOException {
		ClusterArtifact artifact = executionContext.getServiceContext().getResolver(ClusterArtifact.class).resolve(clusterId);
		if (artifact == null) {
			throw new IllegalArgumentException("The id is not a cluster: " + clusterId);
		}
		return artifact.getConfiguration().getHosts();
	}
	
	@WebResult(name = "clusterId")
	public String getClusterFor(@WebParam(name = "host") @NotNull String host) {
		Collection<ClusterArtifact> artifacts = ((ListableServiceContext) executionContext.getServiceContext()).getArtifacts(ClusterArtifact.class);
		for (ClusterArtifact artifact : artifacts) {
			if (artifact.getConfig().getHosts() != null && artifact.getConfig().getHosts().contains(host)) {
				return artifact.getId();
			}
		}
		return null;
	}
	
	public Object invoke(@WebParam(name = "host") String host, @WebParam(name = "serviceId") String id, @WebParam(name = "input") Object input, @WebParam(name = "asynchronous") Boolean asynchronous) throws ServiceException, IOException, InterruptedException, ExecutionException {
		if (id == null) {
			return id;
		}
		DefinedService service = DefinedServiceResolverFactory.getInstance().getResolver().resolve(id);
		if (service == null) {
			throw new IllegalArgumentException("Service not found: " + id);
		}
		ServiceRunner runner;
		if (host == null) {
			runner = EAIResourceRepository.getInstance().getServiceRunner();
		}
		else {
			String clusterId = getClusterFor(host);
			if (clusterId == null) {
				throw new IllegalArgumentException("No cluster found that contains the host '" + host + "'");
			}
			ClusterArtifact resolve = executionContext.getServiceContext().getResolver(ClusterArtifact.class).resolve(clusterId);
			if (resolve != null) {
				ServerConnection connection = resolve.getConnection(host);
				if (connection == null) {
					throw new IllegalArgumentException("Can not get connection for host '" + host + "'");
				}
				runner = connection.getRemote();
			}
			else {
				throw new IllegalArgumentException("Can not resolve cluster '" + clusterId + "'");
			}
		}
		if (runner == null) {
			throw new IllegalStateException("No service runner found for host '" + host + "'");
		}
		ComplexContent serviceInput = new MaskedContent((ComplexContent) input, service.getServiceInterface().getInputDefinition());

		Future<ServiceResult> run = runner.run(service, executionContext, serviceInput);
		
		if (asynchronous == null || !asynchronous) {
			ServiceResult serviceResult = run.get();
			if (serviceResult.getException() != null) {
				throw serviceResult.getException();
			}
			return serviceResult.getOutput();
		}
		return null;
	}
}
