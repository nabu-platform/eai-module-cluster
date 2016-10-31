package be.nabu.eai.module.cluster;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "cluster")
@XmlType(propOrder = { "sharedRepository", "hosts", "simulate" })
public class ClusterConfiguration {
	
	private boolean simulate;
	private List<String> hosts;
	private Boolean sharedRepository;
	
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
}
