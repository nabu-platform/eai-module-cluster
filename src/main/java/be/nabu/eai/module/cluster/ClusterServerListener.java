package be.nabu.eai.module.cluster;

import java.net.CookieManager;
import java.net.CookiePolicy;

import nabu.misc.cluster.Services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.util.SystemPrincipal;
import be.nabu.eai.server.Server;
import be.nabu.eai.server.api.ServerListener;
import be.nabu.libs.events.api.EventSubscription;
import be.nabu.libs.http.api.HTTPRequest;
import be.nabu.libs.http.api.HTTPResponse;
import be.nabu.libs.http.api.server.HTTPServer;
import be.nabu.libs.http.client.DefaultHTTPClient;
import be.nabu.libs.http.client.SPIAuthenticationHandler;
import be.nabu.libs.http.client.connections.PlainConnectionHandler;
import be.nabu.libs.http.core.CustomCookieStore;
import be.nabu.libs.http.server.HTTPServerUtils;
import be.nabu.utils.bully.BullyClient;
import be.nabu.utils.bully.MasterController;

// TODO: add an endpoint where new servers can automatically register themselves within the cluster
// add the hosts to the cluster module itself
public class ClusterServerListener implements ServerListener {

	private Logger logger = LoggerFactory.getLogger(getClass());
	private BullyClient bullyClient;
	
	@Override
	public void listen(Server server, HTTPServer httpServer) {
		try {
			final ClusterArtifact cluster = Services.getOwnCluster(server.getRepository().newExecutionContext(SystemPrincipal.ROOT));
			// only need to care if we are actually in a cluster
			if (cluster != null) {
				logger.info("Part of cluster '" + cluster.getId() + "' with " + cluster.getConfig().getHosts().size() + " hosts");
				// only interesting if the cluster actually has more than one host
				if (cluster.getConfig().getHosts().size() > 1) {
					String self = Services.getOwnHostName(cluster);
					if (self == null) {
						logger.error("Could not find our own server in the cluster");
					}
					else {
						logger.info("Identity within cluster: " + self);
						DefaultHTTPClient httpClient = new DefaultHTTPClient(new PlainConnectionHandler(null, 60*1000, 60*1000*2), new SPIAuthenticationHandler(), new CookieManager(new CustomCookieStore(), CookiePolicy.ACCEPT_ALL), false);
						bullyClient = new BullyClient(self, "/cluster", new MasterController() {
							@Override
							public void setMaster(String master) {
								cluster.setMaster(master);								
							}
						}, 60l*1000, httpClient, null, false, cluster.getConfig().getHosts());
						cluster.setBullyClient(bullyClient);
						// register the listener
						EventSubscription<HTTPRequest, HTTPResponse> subscribe = httpServer.getDispatcher().subscribe(HTTPRequest.class, bullyClient.newHandler());
						subscribe.filter(HTTPServerUtils.limitToPath("/cluster/bully"));
						// start elections!
						bullyClient.scheduleElection(true);
					}
				}
			}
			else {
				logger.info("This server is not part of a cluster");
			}
		}
		catch (Exception e) {
			logger.error("Could not start clustering", e);
		}
	}

	/**
	 * Ideally this should be the last listener of the bunch
	 * Everything else should start (in a cluster) being inactive and only activated once the election process is completed
	 */
	@Override
	public Phase getPhase() {
		return Phase.ARTIFACTS_STARTED;
	}

	@Override
	public Priority getPriority() {
		return Priority.LOW;
	}
	
}
