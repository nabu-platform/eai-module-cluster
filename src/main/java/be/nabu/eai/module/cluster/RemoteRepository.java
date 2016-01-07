package be.nabu.eai.module.cluster;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.Principal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import be.nabu.eai.repository.EAIRepositoryUtils;
import be.nabu.eai.repository.EAIResourceRepository;
import be.nabu.eai.repository.api.ArtifactManager;
import be.nabu.eai.repository.api.ArtifactRepositoryManager;
import be.nabu.eai.repository.api.Entry;
import be.nabu.eai.repository.api.ModifiableEntry;
import be.nabu.eai.repository.api.Node;
import be.nabu.eai.repository.api.ResourceEntry;
import be.nabu.eai.repository.api.ResourceRepository;
import be.nabu.eai.repository.events.RepositoryEvent;
import be.nabu.eai.repository.events.RepositoryEvent.RepositoryState;
import be.nabu.eai.repository.resources.RepositoryEntry;
import be.nabu.libs.artifacts.api.Artifact;
import be.nabu.libs.events.api.EventDispatcher;
import be.nabu.libs.events.impl.EventDispatcherImpl;
import be.nabu.libs.metrics.api.MetricInstance;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.services.api.DefinedService;
import be.nabu.libs.services.api.ExecutionContext;
import be.nabu.libs.services.api.ServiceRunner;

public class RemoteRepository implements ResourceRepository {

	private ResourceRepository local;
	private EventDispatcher dispatcher = new EventDispatcherImpl();
	private RepositoryEntry root;
	private Charset charset = Charset.forName("UTF-8");
	private Map<Class<? extends Artifact>, Map<String, Node>> nodesByType;
	private Logger logger = LoggerFactory.getLogger(getClass());
	private Map<String, List<String>> references = new HashMap<String, List<String>>(), dependencies = new HashMap<String, List<String>>();
	private boolean isLoading;
	private boolean allowLocalLookup;

	public RemoteRepository(ResourceRepository local, ResourceContainer<?> root) {
		this.local = local;
		this.root = new RepositoryEntry(this, root, null, root.getName());
	}
	
	@Override
	public ResourceEntry getRoot() {
		return root;
	}

	@Override
	public Entry getEntry(String id) {
		Entry entry = EAIRepositoryUtils.getEntry(getRoot(), id);
		if (entry == null && allowLocalLookup) {
			entry = local.getEntry(id);
		}
		return entry;
	}

	@Override
	public Charset getCharset() {
		return charset;
	}

	@Override
	public EventDispatcher getEventDispatcher() {
		return dispatcher;
	}

	@Override
	public Node getNode(String id) {
		Node node = EAIRepositoryUtils.getNode(this, id);
		if (node == null) {
			node = local.getNode(id);
		}
		return node;
	}

	@Override
	public void reload(String id) {
		reload(id, true);
	}
	
	private void reload(String id, boolean recursiveReload) {
		logger.info("Reloading: " + id);
		if (recursiveReload) {
			getEventDispatcher().fire(new RepositoryEvent(RepositoryState.RELOAD, false), this);
		}
		Entry entry = getEntry(id);
		// if we have an entry on the root which is not found, it could be new, reset the root (if possible) and try again
		// alternatively we can reload the entire root folder but this would have massive performance repercussions
		if (entry == null && !id.contains(".")) {
			getRoot().refresh(false);
			entry = getEntry(id);
		}
		if (recursiveReload) {
			while (entry == null && id.contains(".")) {
				int index = id.lastIndexOf('.');
				id = id.substring(0, index);
				entry = getEntry(id);
			}
		}
		if (entry != null) {
			unload(entry);
			load(entry);
			// also reload all the dependencies
			// prevent concurrent modification
			if (recursiveReload) {
				Set<String> dependenciesToReload = calculateDependenciesToReload(entry);
				for (String dependency : dependenciesToReload) {
					reload(dependency, false);
				}
			}
		}
		if (recursiveReload) {
			EAIResourceRepository.getInstance().reattachMavenArtifacts(root);
			getEventDispatcher().fire(new RepositoryEvent(RepositoryState.RELOAD, true), this);
		}
	}
	
	private Set<String> calculateDependenciesToReload(Entry entry) {
		Set<String> dependencies = new HashSet<String>();
		if (entry.isNode()) {
			dependencies.addAll(calculateDependenciesToReload(entry.getId()));
		}
		if (!entry.isLeaf()) {
			for (Entry child : entry) {
				Set<String> calculateDependenciesToReload = calculateDependenciesToReload(child);
				dependencies.removeAll(calculateDependenciesToReload);
				dependencies.addAll(calculateDependenciesToReload);
			}
		}
		return dependencies;
	}
	
	private Set<String> calculateDependenciesToReload(String id) {
		List<String> directDependencies = getDependencies(id);
		Set<String> dependenciesToReload = new LinkedHashSet<String>(directDependencies);
		for (String directDependency : directDependencies) {
			Set<String> indirectDependencies = calculateDependenciesToReload(directDependency);
			// remove any dependencies that are also in the indirect ones
			// we can add them again afterwards which means they will only be in the list once and in the correct order
			dependenciesToReload.removeAll(indirectDependencies);
			dependenciesToReload.addAll(indirectDependencies);
		}
		return dependenciesToReload;
	}

	@Override
	public void unload(String id) {
		Entry entry = getEntry(id);
		if (entry != null) {
			unload(entry);
			if (entry.getParent() instanceof ModifiableEntry) {
				((ModifiableEntry) entry.getParent()).removeChildren(entry.getName());
			}
			else {
				entry.getParent().refresh(false);
			}
		}		
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void unload(Entry entry) {
		entry.refresh(false);
		reset();
		if (entry.isNode()) {
			unbuildReferenceMap(entry.getId());
			// if there is an artifact manager and it maintains a repository, remove it all
			if (entry.getNode().isLoaded() && entry.getNode().getArtifactManager() != null && ArtifactRepositoryManager.class.isAssignableFrom(entry.getNode().getArtifactManager())) {
				try {
					((ArtifactRepositoryManager) entry.getNode().getArtifactManager().newInstance()).removeChildren((ModifiableEntry) entry, entry.getNode().getArtifact());
				}
				catch (Exception e) {
					logger.error("Could not finish unloading generated children for " + entry.getId(), e);
				}
			}
		}
		if (!entry.isLeaf()) {
			for (Entry child : entry) {
				unload(child);
			}
		}
	}

	@Override
	public List<Node> getNodes(Class<? extends Artifact> artifactClazz) {
		// TODO: allow local lookup!!
		if (nodesByType == null) {
			scanForTypes();
		}
		List<Node> nodes = new ArrayList<Node>();
		for (Class<?> clazz : nodesByType.keySet()) {
			if (artifactClazz.isAssignableFrom(clazz)) {
				nodes.addAll(nodesByType.get(clazz).values());
			}
		}
		return nodes;
	}

	@Override
	public ServiceRunner getServiceRunner() {
		return local.getServiceRunner();
	}

	@Override
	public void setServiceRunner(ServiceRunner serviceRunner) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<String> getReferences(String id) {
		if (references.containsKey(id)) {
			return new ArrayList<String>(references.get(id));
		}
		else if (allowLocalLookup) {
			return local.getReferences(id);
		}
		else {
			return new ArrayList<String>(); 
		}
	}

	@Override
	public List<String> getDependencies(String id) {
		if (dependencies.containsKey(id)) {
			return new ArrayList<String>(dependencies.get(id));
		}
		else if (allowLocalLookup) {
			return local.getDependencies(id);
		}
		else {
			return new ArrayList<String>(); 
		}
	}

	@Override
	public void start() {
		getEventDispatcher().fire(new RepositoryEvent(RepositoryState.LOAD, false), this);
		isLoading = true;
		load(getRoot());
		isLoading = false;
		// IMPORTANT: this assumes the local server artifacts are in sync with the remote ones!! [IN-SYNC]
		// It is trivial to have multiple module versions in memory, it is however hard in the other repositories to know which one to use
		EAIResourceRepository.getInstance().reattachMavenArtifacts(root);
		getEventDispatcher().fire(new RepositoryEvent(RepositoryState.LOAD, true), this);
	}

	private void buildReferenceMap(String id, List<String> references) {
		if (references != null) {
			this.references.put(id, references);
			for (String reference : references) {
				if (!dependencies.containsKey(reference)) {
					dependencies.put(reference, new ArrayList<String>());
				}
				dependencies.get(reference).add(id);
			}
		}
	}
	private void unbuildReferenceMap(String id) {
		this.references.remove(id);
		for (String dependency : dependencies.keySet()) {
			if (dependencies.get(dependency).contains(id)) {
				dependencies.get(dependency).remove(id);
			}
		}
	}
	
	private void load(Entry entry, List<Entry> artifactRepositoryManagers) {
		// don't refresh on initial load, this messes up performance for remote file systems
		if (!isLoading) {
			// refresh every entry before reloading it, there could be new elements (e.g. remote changes to repo)
			entry.refresh(false);
			// reset this to make sure any newly loaded entries are picked up or old entries are deleted
			reset();
		}
		if (entry.isNode()) {
			logger.info("Loading entry: " + entry.getId());
			buildReferenceMap(entry.getId(), entry.getNode().getReferences());
			if (entry instanceof ModifiableEntry && entry.isNode() && entry.getNode().getArtifactManager() != null && ArtifactRepositoryManager.class.isAssignableFrom(entry.getNode().getArtifactManager())) {
				artifactRepositoryManagers.add(entry);
			}
		}
		if (!entry.isLeaf()) {
			for (Entry child : entry) {
				load(child, artifactRepositoryManagers);
			}
		}
	}
	
	private void reset() {
		nodesByType = null;
	}
	
	private void load(Entry entry) {
		logger.info("Loading: " + entry.getId());
		List<Entry> artifactRepositoryManagers = new ArrayList<Entry>();
		load(entry, artifactRepositoryManagers);
		// first load the repositories without dependencies
		for (Entry manager : artifactRepositoryManagers) {
			if (manager.getNode().getReferences() == null || manager.getNode().getReferences().isEmpty()) {
				loadArtifactManager(manager);
			}
		}
		// then the rest
		for (Entry manager : artifactRepositoryManagers) {
			if (manager.getNode().getReferences() != null && !manager.getNode().getReferences().isEmpty()) {
				loadArtifactManager(manager);
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void loadArtifactManager(Entry entry) {
		logger.debug("Loading children of: " + entry.getId());
		try {
			Artifact artifact = entry.getNode().getArtifact();
			if (artifact != null) {
				List<Entry> addedChildren = ((ArtifactRepositoryManager) entry.getNode().getArtifactManager().newInstance()).addChildren((ModifiableEntry) entry, artifact);
				if (addedChildren != null) {
					for (Entry addedChild : addedChildren) {
						buildReferenceMap(addedChild.getId(), addedChild.getNode().getReferences());
					}
				}
			}
		}
		catch (Exception e) {
			logger.error("Could not finish loading generated children for: " + entry.getId(), e);
		}
	}

	@Override
	public ClassLoader newClassLoader(String artifact) {
		return local.newClassLoader(artifact);
	}

	@Override
	public Artifact resolve(String id) {
		Artifact resolve = EAIRepositoryUtils.resolve(this, id);
		if (resolve == null && allowLocalLookup) {
			resolve = local.resolve(id);
		}
		return resolve;
	}

	@Override
	public ExecutionContext newExecutionContext(Principal principal) {
		return local.newExecutionContext(principal);
	}

	@Override
	public List<DefinedService> getServices() {
		List<Node> nodes = getNodes(DefinedService.class);
		List<DefinedService> services = new ArrayList<DefinedService>(nodes.size());
		for (Node node : nodes) {
			try {
				services.add((DefinedService) node.getArtifact());
			}
			catch (IOException e) {
				logger.error("Could not load " + node, e);
			}
			catch (ParseException e) {
				logger.error("Could not load " + node, e);
			}
		}
		return services;
	}

	@Override
	public boolean isInternal(ResourceContainer<?> container) {
		return local.isInternal(container);
	}

	@Override
	public boolean isValidName(ResourceContainer<?> parent, String name) {
		return local.isValidName(parent, name);
	}
	
	private void scanForTypes() {
		if (nodesByType == null) {
			synchronized(this) {
				if (nodesByType == null) {
					nodesByType = new HashMap<Class<? extends Artifact>, Map<String, Node>>();
				}
			}
		}
		synchronized(nodesByType) {
			nodesByType.clear();
			scanForTypes(getRoot());
		}
	}
	
	private void scanForTypes(Entry entry) {
		if (nodesByType == null) {
			synchronized(this) {
				nodesByType = new HashMap<Class<? extends Artifact>, Map<String, Node>>();
			}
		}
		synchronized(nodesByType) {
			for (Entry child : entry) {
				if (child.isNode()) {
					Class<? extends Artifact> artifactClass = child.getNode().getArtifactClass();
					if (!nodesByType.containsKey(artifactClass)) {
						nodesByType.put(artifactClass, new HashMap<String, Node>());
					}
					nodesByType.get(artifactClass).put(child.getId(), child.getNode());
				}
				if (!child.isLeaf()) {
					scanForTypes(child);
				}
			}
		}
	}

	@Override
	public <T> List<Class<T>> getImplementationsFor(Class<T> clazz) {
		return local.getImplementationsFor(clazz);
	}

	@Override
	public <T extends Artifact> List<T> getArtifacts(Class<T> artifactClazz) {
		List<T> artifacts = EAIRepositoryUtils.getArtifacts(this, artifactClazz);
		if (allowLocalLookup) {
			List<String> ids = new ArrayList<String>();
			for (T artifact : artifacts) {
				ids.add(artifact.getId());
			}
			for (T artifact : local.getArtifacts(artifactClazz)) {
				if (!ids.contains(artifact.getId())) {
					artifacts.add(artifact);
				}
			}
		}
		return artifacts;
	}

	public boolean isAllowLocalLookup() {
		return allowLocalLookup;
	}

	public void setAllowLocalLookup(boolean allowLocalLookup) {
		this.allowLocalLookup = allowLocalLookup;
	}
	
	@Override
	public void reloadAll() {
		unload(getRoot());
		references.clear();
		dependencies.clear();
		reset();
		load(getRoot());
	}

	@Override
	public MetricInstance getMetricInstance(String id) {
		return null;
	}

	@Override
	public <T extends Artifact> ArtifactManager<T> getArtifactManager(Class<T> artifactClass) {
		return local.getArtifactManager(artifactClass);
	}

	@Override
	public void reloadAll(Collection<String> ids) {
		getEventDispatcher().fire(new RepositoryEvent(RepositoryState.RELOAD, false), this);
		Set<String> dependenciesToReload = new HashSet<String>();
		for (String id : ids) {
			Set<String> calculateDependenciesToReload = calculateDependenciesToReload(id);
			dependenciesToReload.removeAll(calculateDependenciesToReload);
			dependenciesToReload.addAll(calculateDependenciesToReload);
		}
		for (String id : dependenciesToReload) {
			reload(id, false);
		}
		EAIResourceRepository.getInstance().reattachMavenArtifacts(root);
		getEventDispatcher().fire(new RepositoryEvent(RepositoryState.RELOAD, true), this);
	}

}
