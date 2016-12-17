package be.nabu.eai.module.cluster;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "cluster")
@XmlType(propOrder = { "sharedRepository", "hosts", "simulate", "uri", "connectionTimeout", "socketTimeout" })
public class ClusterConfiguration {
	/**
	 * The uri where the simulation data (if any) is stored
	 */
	private URI uri;
	private boolean simulate;
	private List<String> hosts;
	private Boolean sharedRepository;
	private Integer connectionTimeout, socketTimeout;
	
	public List<String> getHosts() {
		return hosts;
	}
	public void setHosts(List<String> hosts) {
		this.hosts = hosts;
	}
	public Boolean getSharedRepository() {
		return sharedRepository;
	}
	public void setSharedRepository(Boolean sharedRepository) {
		this.sharedRepository = sharedRepository;
	}
	public boolean isSimulate() {
		return simulate;
	}
	public void setSimulate(boolean simulate) {
		this.simulate = simulate;
	}
	public Integer getConnectionTimeout() {
		return connectionTimeout;
	}
	public void setConnectionTimeout(Integer connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
	public Integer getSocketTimeout() {
		return socketTimeout;
	}
	public void setSocketTimeout(Integer socketTimeout) {
		this.socketTimeout = socketTimeout;
	}
	public URI getUri() {
		return uri;
	}
	public void setUri(URI uri) {
		this.uri = uri;
	}
}
