/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.cluster;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import be.nabu.eai.api.Comment;
import be.nabu.eai.api.EnvironmentSpecific;

@XmlRootElement(name = "cluster")
@XmlType(propOrder = { "sharedRepository", "hosts", "path", "simulate", "uri", "connectionTimeout", "socketTimeout", "localLookupRegex", "secure" })
public class ClusterConfiguration {
	/**
	 * The uri where the simulation data (if any) is stored
	 */
	private URI uri;
	private boolean simulate;
	private List<String> hosts;
	private Boolean sharedRepository;
	private Integer connectionTimeout, socketTimeout;
	private String localLookupRegex, path;
	private Boolean secure;
	
	@EnvironmentSpecific
	public List<String> getHosts() {
		return hosts;
	}
	public void setHosts(List<String> hosts) {
		this.hosts = hosts;
	}
	
	@EnvironmentSpecific
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
	public String getLocalLookupRegex() {
		return localLookupRegex;
	}
	public void setLocalLookupRegex(String localLookupRegex) {
		this.localLookupRegex = localLookupRegex;
	}
	public Boolean getSecure() {
		return secure;
	}
	public void setSecure(Boolean secure) {
		this.secure = secure;
	}
	@Comment(title = "If the server is not accessible on the root but on some other path, configure that here (not relevant for simulations)")
	public String getPath() {
		return path;
	}
	public void setPath(String path) {
		this.path = path;
	}
}
