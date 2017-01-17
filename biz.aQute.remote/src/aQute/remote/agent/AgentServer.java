package aQute.remote.agent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.dto.BundleDTO;
import org.osgi.framework.dto.FrameworkDTO;
import org.osgi.framework.dto.ServiceReferenceDTO;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.FrameworkWiring;
import org.osgi.framework.wiring.dto.BundleRevisionDTO;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.dto.CapabilityDTO;
import org.osgi.resource.dto.RequirementDTO;

import aQute.bnd.osgi.repository.XMLResourceGenerator;
import aQute.lib.converter.Converter;
import aQute.lib.converter.TypeReference;
import aQute.libg.shacache.ShaCache;
import aQute.libg.shacache.ShaSource;
import aQute.remote.api.Agent;
import aQute.remote.api.Event;
import aQute.remote.api.Event.Type;
import aQute.remote.api.Supervisor;
import aQute.remote.util.Link;

/**
 * Implementation of the Agent. This implementation implements the Agent
 * interfaces and communicates with a Supervisor interfaces.
 */
public class AgentServer implements Agent, Closeable, FrameworkListener {
	AtomicInteger											sequence			= new AtomicInteger(1000);

	//
	// Constant so we do not have to repeat it
	//

	private static final TypeReference<Map<String,String>>	MAP_STRING_STRING_T	= new TypeReference<Map<String,String>>() {};

	private static final long[]								EMPTY				= new long[0];

	private static final String								UTF_8				= "UTF-8";

	//
	// Known keys in the framework properties since we cannot
	// iterate over framework properties
	//

	@SuppressWarnings("deprecation")
	static String											keys[]				= {
																						Constants.FRAMEWORK_BEGINNING_STARTLEVEL,
																						Constants.FRAMEWORK_BOOTDELEGATION,
																						Constants.FRAMEWORK_BSNVERSION,
																						Constants.FRAMEWORK_BUNDLE_PARENT,
																						Constants.FRAMEWORK_TRUST_REPOSITORIES,
																						Constants.FRAMEWORK_COMMAND_ABSPATH,
																						Constants.FRAMEWORK_EXECPERMISSION,
																						Constants.FRAMEWORK_EXECUTIONENVIRONMENT,
																						Constants.FRAMEWORK_LANGUAGE,
																						Constants.FRAMEWORK_LIBRARY_EXTENSIONS,
																						Constants.FRAMEWORK_OS_NAME,
																						Constants.FRAMEWORK_OS_VERSION,
																						Constants.FRAMEWORK_PROCESSOR,
																						Constants.FRAMEWORK_SECURITY,
																						Constants.FRAMEWORK_STORAGE,
																						Constants.FRAMEWORK_SYSTEMCAPABILITIES,
																						Constants.FRAMEWORK_SYSTEMCAPABILITIES_EXTRA,
																						Constants.FRAMEWORK_SYSTEMPACKAGES,
																						Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA,
																						Constants.FRAMEWORK_UUID,
																						Constants.FRAMEWORK_VENDOR,
																						Constants.FRAMEWORK_VERSION,
																						Constants.FRAMEWORK_WINDOWSYSTEM,
																					};

	private Supervisor										remote;
	private BundleContext									context;
	private final ShaCache									cache;
	private ShaSource										source;
	private final Map<String,String>						installed			= new HashMap<String,String>();
	volatile boolean										quit;
	private static Map<String,AgentDispatcher>				instances			= new HashMap<String,AgentDispatcher>();
	private Redirector										redirector			= new NullRedirector();
	private Link<Agent,Supervisor>							link;
	private CountDownLatch									refresh				= new CountDownLatch(0);

	/**
	 * An agent server is based on a context and takes a name and cache
	 * directory
	 * 
	 * @param name the name of the agent's framework
	 * @param context a bundle context of the framework
	 * @param cache the directory for caching
	 */
	public AgentServer(String name, BundleContext context, File cache) {
		this.context = context;
		if (this.context != null)
			this.context.addFrameworkListener(this);

		this.cache = new ShaCache(cache);
	}

	/**
	 * Get the framework's DTO
	 */
	@Override
	public FrameworkDTO getFramework() throws Exception {
		FrameworkDTO fw = new FrameworkDTO();
		fw.bundles = getBundles();
		fw.properties = getProperties();
		fw.services = getServiceReferences();
		return fw;
	}

	@Override
	public String indexFramework() throws Exception {
		List<Resource> resources = new ArrayList<>();
		for (Bundle bundle : context.getBundles()) {
			BundleRevision bundleRevision = bundle.adapt(BundleRevision.class);
			resources.add(bundleRevision);
		}
		XMLResourceGenerator generator = new XMLResourceGenerator();
		generator.resources(resources);
		generator.name("Framework " + getProperties().get(Constants.FRAMEWORK_UUID));
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		generator.save(outputStream);
		return outputStream.toString(UTF_8);
	}

	@Override
	public BundleDTO install(String location, String sha) throws Exception {
		InputStream in = cache.getStream(sha, source);
		if (in == null)
			return null;

		Bundle b = context.installBundle(location, in);
		installed.put(b.getLocation(), sha);
		return toDTO(b);
	}

	@Override
	public BundleDTO installFromURL(String location, String url) throws Exception {
		InputStream is = new URL(url).openStream();
		Bundle b = context.installBundle(location, is);
		installed.put(b.getLocation(), url);
		return toDTO(b);
	}

	@Override
	public String start(long... ids) {
		StringBuilder sb = new StringBuilder();

		for (long id : ids) {
			Bundle bundle = context.getBundle(id);
			try {
				bundle.start();
			} catch (BundleException e) {
				sb.append(e.getMessage()).append("\n");
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	@Override
	public String stop(long... ids) {
		StringBuilder sb = new StringBuilder();

		for (long id : ids) {
			Bundle bundle = context.getBundle(id);
			try {
				bundle.stop();
			} catch (BundleException e) {
				sb.append(e.getMessage()).append("\n");
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	@Override
	public String uninstall(long... ids) {
		StringBuilder sb = new StringBuilder();

		for (long id : ids) {
			Bundle bundle = context.getBundle(id);
			try {
				bundle.uninstall();
				installed.remove(bundle.getBundleId());
			} catch (BundleException e) {
				sb.append(e.getMessage()).append("\n");
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	@Override
	public String update(Map<String,String> bundles) throws InterruptedException {

		refresh.await();

		Formatter out = new Formatter();
		if (bundles == null) {
			bundles = Collections.emptyMap();
		}

		Set<String> toBeDeleted = new HashSet<String>(installed.keySet());
		toBeDeleted.removeAll(bundles.keySet());

		LinkedHashSet<String> toBeInstalled = new LinkedHashSet<String>(bundles.keySet());
		toBeInstalled.removeAll(installed.keySet());

		Map<String,String> changed = new HashMap<String,String>(bundles);
		changed.values().removeAll(installed.values());
		changed.keySet().removeAll(toBeInstalled);

		Set<String> affected = new HashSet<String>(toBeDeleted);
		affected.addAll(changed.keySet());

		LinkedHashSet<Bundle> toBeStarted = new LinkedHashSet<Bundle>();

		for (String location : affected) {
			Bundle b = getBundle(location);
			if (b == null) {
				out.format("Could not location bundle %s to stop it", location);
				continue;
			}

			try {
				if (isActive(b))
					toBeStarted.add(b);

				b.stop();
			} catch (Exception e) {
				printStack(e);
				out.format("Trying to stop bundle %s : %s", b, e);
			}

		}

		for (String location : toBeDeleted) {
			Bundle b = getBundle(location);
			if (b == null) {
				out.format("Could not find bundle %s to uninstall it", location);
				continue;
			}

			try {
				b.uninstall();
				installed.remove(location);
				toBeStarted.remove(b);
			} catch (Exception e) {
				printStack(e);
				out.format("Trying to uninstall %s: %s", location, e);
			}
		}

		for (String location : toBeInstalled) {
			String sha = bundles.get(location);

			try {
				InputStream in = cache.getStream(sha, source);
				if (in == null) {
					out.format("Could not find file with sha %s for bundle %s", sha, location);
					continue;
				}

				Bundle b = context.installBundle(location, in);
				installed.put(location, sha);
				toBeStarted.add(b);

			} catch (Exception e) {
				printStack(e);
				out.format("Trying to install %s: %s", location, e);
			}
		}

		for (Entry<String,String> e : changed.entrySet()) {
			String location = e.getKey();
			String sha = e.getValue();

			try {
				InputStream in = cache.getStream(sha, source);
				if (in == null) {
					out.format("Cannot find file for sha %s to update %s", sha, location);
					continue;
				}

				Bundle bundle = getBundle(location);
				if (bundle == null) {
					out.format("No such bundle for location %s while trying to update it", location);
					continue;
				}

				if (bundle.getState() == Bundle.UNINSTALLED)
					context.installBundle(location, in);
				else
					bundle.update(in);

			} catch (Exception e1) {
				printStack(e1);
				out.format("Trying to update %s: %s", location, e);
			}
		}

		for (Bundle b : toBeStarted) {
			try {
				b.start();
			} catch (Exception e1) {
				printStack(e1);
				out.format("Trying to start %s: %s", b, e1);
			}
		}

		String result = out.toString();
		out.close();
		if (result.length() == 0) {
			refresh(true);
			return null;
		}

		return result;
	}

	public String update(long id, String sha) throws Exception {
		InputStream in = cache.getStream(sha, source);
		if (in == null)
			return null;

		StringBuilder sb = new StringBuilder();

		try {
			Bundle bundle = context.getBundle(id);
			bundle.update(in);
			refresh(true);
		} catch (Exception e) {
			sb.append(e.getMessage()).append("\n");
		}

		return sb.length() == 0 ? null : sb.toString();
	}

	public String updateFromURL(long id, String url) throws Exception {
		StringBuilder sb = new StringBuilder();
		InputStream is = new URL(url).openStream();

		try {
			Bundle bundle = context.getBundle(id);
			bundle.update(is);
			refresh(true);
		} catch (Exception e) {
			sb.append(e.getMessage()).append("\n");
		}

		return sb.length() == 0 ? null : sb.toString();
	}

	private Bundle getBundle(String location) {
		try {
			Bundle bundle = context.getBundle(location);
			return bundle;
		} catch (Exception e) {
			printStack(e);
		}
		return null;
	}

	private boolean isActive(Bundle b) {
		return b.getState() == Bundle.ACTIVE || b.getState() == Bundle.STARTING;
	}

	@Override
	public boolean redirect(int port) throws Exception {
		if (redirector != null) {
			if (redirector.getPort() == port)
				return false;

			redirector.close();
			redirector = new NullRedirector();
		}

		if (port == Agent.NONE)
			return true;

		if (port <= Agent.COMMAND_SESSION) {
			try {
				redirector = new GogoRedirector(this, context);
			} catch (Exception e) {
				throw new IllegalStateException("Gogo is not present in this framework", e);
			}
			return true;
		}

		if (port == Agent.CONSOLE) {
			redirector = new ConsoleRedirector(this);
			return true;
		}

		redirector = new SocketRedirector(this, port);
		return true;
	}

	@Override
	public boolean stdin(String s) throws Exception {
		if (redirector != null) {
			redirector.stdin(s);
			return true;
		}
		return false;
	}

	@Override
	public String shell(String cmd) {
		// TODO Auto-generated method stub
		return null;
	}

	public void setSupervisor(Supervisor remote) {
		setRemote(remote);
	}

	private List<ServiceReferenceDTO> getServiceReferences() throws Exception {
		ServiceReference< ? >[] refs = context.getAllServiceReferences(null, null);
		if (refs == null)
			return Collections.emptyList();

		ArrayList<ServiceReferenceDTO> list = new ArrayList<ServiceReferenceDTO>(refs.length);
		for (ServiceReference< ? > r : refs) {
			ServiceReferenceDTO ref = new ServiceReferenceDTO();
			ref.bundle = r.getBundle().getBundleId();
			ref.id = (Long) r.getProperty(Constants.SERVICE_ID);
			ref.properties = getProperties(r);
			Bundle[] usingBundles = r.getUsingBundles();
			if (usingBundles == null)
				ref.usingBundles = EMPTY;
			else {
				ref.usingBundles = new long[usingBundles.length];
				for (int i = 0; i < usingBundles.length; i++) {
					ref.usingBundles[i] = usingBundles[i].getBundleId();
				}
			}
			list.add(ref);
		}
		return list;
	}

	private Map<String,Object> getProperties(ServiceReference< ? > ref) {
		Map<String,Object> map = new HashMap<String,Object>();
		for (String key : ref.getPropertyKeys())
			map.put(key, ref.getProperty(key));
		return map;
	}

	@SuppressWarnings({
			"unchecked", "rawtypes"
	})
	private Map<String,Object> getProperties() {
		Map map = new HashMap();
		map.putAll(System.getenv());
		map.putAll(System.getProperties());
		for (String key : keys) {
			Object value = context.getProperty(key);
			if (value != null)
				map.put(key, value);
		}
		return map;
	}

	private List<BundleDTO> getBundles() {
		Bundle[] bundles = context.getBundles();
		ArrayList<BundleDTO> list = new ArrayList<BundleDTO>(bundles.length);
		for (Bundle b : bundles) {
			list.add(toDTO(b));
		}
		return list;
	}

	private BundleDTO toDTO(Bundle b) {
		BundleDTO bd = new BundleDTO();
		bd.id = b.getBundleId();
		bd.lastModified = b.getLastModified();
		bd.state = b.getState();
		bd.symbolicName = b.getSymbolicName();
		bd.version = b.getVersion() == null ? "0" : b.getVersion().toString();
		return bd;
	}

	void cleanup(int event) throws Exception {
		if (quit)
			return;

		instances.remove(this);
		quit = true;
		update(null);
		redirect(0);
		sendEvent(event);
		link.close();
	}

	@Override
	public void close() throws IOException {
		try {
			cleanup(-2);
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	@Override
	public void abort() throws Exception {
		cleanup(-3);
	}

	private void sendEvent(int code) {
		Event e = new Event();
		e.type = Event.Type.exit;
		e.code = code;
		try {
			remote.event(e);
		} catch (Exception e1) {
			printStack(e1);
		}
	}

	@Override
	public void frameworkEvent(FrameworkEvent event) {
		try {
			Event e = new Event();
			e.type = Type.framework;
			e.code = event.getType();
			remote.event(e);
		} catch (Exception e1) {
			printStack(e1);
		}
	}

	private void printStack(Exception e1) {
		try {
			e1.printStackTrace(redirector.getOut());
		} catch (Exception e) {
			//
		}
	}

	public void setRemote(Supervisor supervisor) {
		this.remote = supervisor;
		this.source = new ShaSource() {

			@Override
			public boolean isFast() {
				return false;
			}

			@Override
			public InputStream get(String sha) throws Exception {
				byte[] data = remote.getFile(sha);
				if (data == null)
					return null;

				return new ByteArrayInputStream(data);
			}
		};

	}

	@Override
	public boolean isEnvoy() {
		return false;
	}

	@Override
	public Map<String,String> getSystemProperties() throws Exception {
		return Converter.cnv(MAP_STRING_STRING_T, System.getProperties());
	}

	@Override
	public boolean createFramework(String name, Collection<String> runpath, Map<String,Object> properties)
			throws Exception {
		throw new UnsupportedOperationException("This is an agent, we can't create new frameworks (for now)");
	}

	public Supervisor getSupervisor() {
		return remote;
	}

	public void setLink(Link<Agent,Supervisor> link) {
		setRemote(link.getRemote());
		this.link = link;
	}

	public boolean ping() {
		return true;
	}

	public BundleContext getContext() {
		return context;
	}

	public void refresh(boolean async) throws InterruptedException {
		FrameworkWiring f = context.getBundle(0).adapt(FrameworkWiring.class);
		if (f != null) {
			refresh = new CountDownLatch(1);
			f.refreshBundles(null, new FrameworkListener() {

				@Override
				public void frameworkEvent(FrameworkEvent event) {
					refresh.countDown();
				}
			});

			if (async)
				return;

			refresh.await();
		}
	}

	@Override
	public List<BundleDTO> getBundles(long... bundleId) throws Exception {

		Bundle[] bundles;
		if (bundleId.length == 0) {
			bundles = context.getBundles();
		} else {
			bundles = new Bundle[bundleId.length];
			for (int i = 0; i < bundleId.length; i++) {
				bundles[i] = context.getBundle(bundleId[i]);
			}
		}

		List<BundleDTO> bundleDTOs = new ArrayList<BundleDTO>(bundles.length);

		for (Bundle b : bundles) {
			BundleDTO dto = toDTO(b);
			bundleDTOs.add(dto);
		}

		return bundleDTOs;
	}

	/**
	 * Return the bundle revisions
	 */
	@Override
	public List<BundleRevisionDTO> getBundleRevisons(long... bundleId) throws Exception {

		Bundle[] bundles;
		if (bundleId.length == 0) {
			bundles = context.getBundles();
		} else {
			bundles = new Bundle[bundleId.length];
			for (int i = 0; i < bundleId.length; i++) {
				bundles[i] = context.getBundle(bundleId[i]);
			}
		}

		List<BundleRevisionDTO> revisions = new ArrayList<BundleRevisionDTO>(bundles.length);

		for (Bundle b : bundles) {
			BundleRevision resource = b.adapt(BundleRevision.class);
			BundleRevisionDTO bwd = toDTO(resource);
			revisions.add(bwd);
		}

		return revisions;
	}

	/*
	 * Turn a bundle in a Bundle Revision dto. On a r6 framework we could do
	 * this with adapt but on earlier frameworks we're on our own
	 */

	private BundleRevisionDTO toDTO(BundleRevision resource) {
		BundleRevisionDTO brd = new BundleRevisionDTO();
		brd.bundle = resource.getBundle().getBundleId();
		brd.id = sequence.getAndIncrement();
		brd.symbolicName = resource.getSymbolicName();
		brd.type = resource.getTypes();
		brd.version = resource.getVersion().toString();

		brd.requirements = new ArrayList<RequirementDTO>();

		for (Requirement r : resource.getRequirements(null)) {
			brd.requirements.add(toDTO(brd.id, r));
		}

		brd.capabilities = new ArrayList<CapabilityDTO>();
		for (Capability c : resource.getCapabilities(null)) {
			brd.capabilities.add(toDTO(brd.id, c));
		}

		return brd;
	}

	private RequirementDTO toDTO(int resource, Requirement r) {
		RequirementDTO rd = new RequirementDTO();
		rd.id = sequence.getAndIncrement();
		rd.resource = resource;
		rd.namespace = r.getNamespace();
		rd.directives = r.getDirectives();
		rd.attributes = r.getAttributes();
		return rd;
	}

	private CapabilityDTO toDTO(int resource, Capability r) {
		CapabilityDTO rd = new CapabilityDTO();
		rd.id = sequence.getAndIncrement();
		rd.resource = resource;
		rd.namespace = r.getNamespace();
		rd.directives = r.getDirectives();
		rd.attributes = r.getAttributes();
		return rd;
	}

}
