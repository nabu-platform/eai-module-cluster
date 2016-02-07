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

import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.module.cluster.ClusterArtifact;
import be.nabu.libs.services.ListableServiceContext;
import be.nabu.libs.services.api.ExecutionContext;

@WebService
public class Services {
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	private ExecutionContext executionContext;
	
	public Services() {
		// auto construct
	}
	
	public Services(ExecutionContext executionContext) {
		this.executionContext = executionContext;
	}
	
	@WebResult(name = "clusterId")
	public String getCurrentCluster() {
		try {
			ClusterArtifact ownCluster = getOwnCluster();
			return ownCluster == null ? null : ownCluster.getId();
		}
		catch (SocketException e) {
			throw new RuntimeException(e);
		}
	}

	private ClusterArtifact getOwnCluster() throws SocketException {
		return getClusterFor(getLocalAddresses());
	}

	private ClusterArtifact getClusterFor(List<String> localAddresses) throws SocketException {
		Collection<ClusterArtifact> artifacts = ((ListableServiceContext) executionContext.getServiceContext()).getArtifacts(ClusterArtifact.class);
		if (artifacts != null && !artifacts.isEmpty()) {
			for (ClusterArtifact artifact : artifacts) {
				try {
					if (artifact.getConfiguration().getHosts() != null) {
						for (String host : artifact.getConfiguration().getHosts()) {
							if (localAddresses.contains(getAddress(host))) {
								return artifact;
							}
						}
					}
				}
				catch (Exception e) {
					logger.error("Could not load cluster configuration for: " + artifact.getId(), e);
				}
			}
		}
		return null;
	}
	
	private String getAddress(String host) throws UnknownHostException {
		InetAddress inetAddress = InetAddress.getByName(host);
		if (inetAddress == null) {
			throw new IllegalArgumentException("Unknown host: " + host);
		}
		return inetAddress.getHostAddress();
	}
	
	private List<String> getLocalAddresses() throws SocketException {
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
		ClusterArtifact ownCluster = getOwnCluster();
		List<String> peers = new ArrayList<String>();
		if (ownCluster != null) {
			List<String> localAddresses = getLocalAddresses();
			for (String host : ownCluster.getConfiguration().getHosts()) {
				String address = getAddress(host);
				if (!localAddresses.contains(address)) {
					peers.add(address);
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
}
