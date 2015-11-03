package be.nabu.eai.module.cluster;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "cluster")
@XmlType(propOrder = { "hosts" })
public class ClusterConfiguration {
	
	private List<String> hosts;
	
	public List<String> getHosts() {
		return hosts;
	}
	public void setHosts(List<String> hosts) {
		this.hosts = hosts;
	}
	
}
