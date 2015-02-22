package aQute.bnd.component;

import java.util.*;

import aQute.bnd.header.*;
import aQute.bnd.osgi.*;
import aQute.bnd.osgi.Descriptors.PackageRef;
import aQute.bnd.service.*;
import aQute.bnd.version.*;
import aQute.lib.strings.*;
import aQute.service.reporter.Reporter.SetLocation;

/**
 * Analyze the class space for any classes that have an OSGi annotation for DS.
 */
public class DSAnnotations implements AnalyzerPlugin {

	enum Flag {
		inherit, felixExtensions, extender
	};

	public boolean analyzeJar(Analyzer analyzer) throws Exception {
		Parameters header = OSGiHeader.parseHeader(analyzer.getProperty(Constants.DSANNOTATIONS));
		if (header.size() == 0)
			return false;

		Parameters flagHeader = OSGiHeader.parseHeader(analyzer.getProperty(Constants.DSANNOTATIONS + "-flags"));
		Set<Flag> flagSet = new HashSet<Flag>();
		for (String s : flagHeader.keySet()) {
			try {
				flagSet.add(Enum.valueOf(Flag.class, s));
			}
			catch (IllegalArgumentException e) {
				analyzer.error(
						"Unrecognized -dsannotations-flags value %s, expected values are %s", s,
						EnumSet.allOf(Flag.class));
			}
		}
		if (Processor.isTrue(analyzer.getProperty("-dsannotations-inherit")))
			flagSet.add(Flag.inherit);
		if (Processor.isTrue(analyzer.getProperty("-ds-felix-extensions")))
			flagSet.add(Flag.felixExtensions);

		EnumSet<Flag> flags = flagSet.isEmpty() ? EnumSet.noneOf(Flag.class) : EnumSet.copyOf(flagSet);

		Instructions instructions = new Instructions(header);
		Collection<Clazz> list = analyzer.getClassspace().values();
		String sc = analyzer.getProperty(Constants.SERVICE_COMPONENT);
		List<String> names = new ArrayList<String>();
		if (sc != null && sc.trim().length() > 0)
			names.add(sc);

		TreeSet<String> provides = new TreeSet<String>();
		TreeSet<String> requires = new TreeSet<String>();
		Version packageVersion = getPackageVersion(analyzer);
		Version maxVersion = AnnotationReader.V1_0;

		for (Clazz c : list) {
			for (Instruction instruction : instructions.keySet()) {

				if (instruction.matches(c.getFQN())) {
					if (instruction.isNegated())
						break;
					ComponentDef definition = AnnotationReader.getDefinition(c, analyzer, flags);
					if (definition != null) {

						definition.sortReferences();
						definition.prepare(analyzer);
						verifyVersion(analyzer, definition, packageVersion);

						String name = "OSGI-INF/"
								+ analyzer.validResourcePath(definition.name, "Invalid component name") + ".xml";
						names.add(name);
						analyzer.getJar().putResource(name, new TagResource(definition.getTag()));

						if (definition.service != null) {
							String[] objectClass = new String[definition.service.length];

							for (int i = 0; i < definition.service.length; i++) {
								Descriptors.TypeRef tr = definition.service[i];
								objectClass[i] = tr.getFQN();
							}
							Arrays.sort(objectClass);
							addProvidesHeader(objectClass, provides);
						}
						for (ReferenceDef ref : definition.references.values()) {
							String objectClass = ref.service;
							addRequireHeader(objectClass, requires);
						}
						maxVersion = definition.max(maxVersion, definition.version);
					}
				}
			}
		}
		if (flags.contains(Flag.extender)
				|| (packageVersion != null && packageVersion.compareTo(AnnotationReader.V1_3) >= 0)
				|| maxVersion.compareTo(AnnotationReader.V1_3) >= 0) {
			addExtenRequireHeader(requires);
		}
		sc = Processor.append(names.toArray(new String[names.size()]));
		analyzer.setProperty(Constants.SERVICE_COMPONENT, sc);
		updateHeader(analyzer, Constants.REQUIRE_CAPABILITY, requires);
		updateHeader(analyzer, Constants.PROVIDE_CAPABILITY, provides);
		return false;
	}

	/**
	 * Verify that the component definition has a version that is <= than the
	 * version of the component package that we build against.
	 * 
	 * @param version
	 *            package version of org.osgi.service.component exported in
	 *            build environment.
	 */
	private void verifyVersion(Analyzer analyzer, ComponentDef definition, Version v) throws Exception {
		if (v != null) {
			if (definition.version.compareTo(v) > 0) {
				SetLocation error = analyzer
						.error("Generating XML for %s in type %s that uses a namespace version %s while you are building against %s",
								definition.name, definition.implementation, definition.version, v);

				error.details(analyzer.getPackageRef("org/osgi/service/component"));
				analyzer.setTypeLocation(error, definition.implementation);
			}
		}
	}

	private Version getPackageVersion(Analyzer analyzer) {
		PackageRef component = analyzer.getPackageRef("org/osgi/service/component");
		Attrs attrs = analyzer.getClasspathExports().get(component);
		if (attrs != null) {
			String version = attrs.getVersion();
			if (version != null && Verifier.isVersion(version)) {
				return new Version(version);
			}
		}
		return null;
	}

	private void addProvidesHeader(String[] objectClass, Set<String> provides) {
		if (objectClass.length > 0) {
			Parameters p = new Parameters();
			Attrs a = new Attrs();
			StringBuilder sb = new StringBuilder();
			String sep = "";
			for (String oc : objectClass) {
				sb.append(sep).append(oc);
				sep = ",";
			}
			a.put("objectClass:List<String>", sb.toString());
			p.put("osgi.service", a);
			String s = p.toString();
			provides.add(s);
		}
	}

	private void addRequireHeader(String objectClass, Set<String> requires) {
		Parameters p = new Parameters();
		Attrs a = new Attrs();
		a.put("filter:", "\"(objectClass=" + objectClass + ")\"");
		a.put("effective:", "\"active\"");
		p.put("osgi.service", a);
		String s = p.toString();
		requires.add(s);
	}

	private void addExtenRequireHeader(Set<String> requires) {
		Parameters p = new Parameters();
		Attrs a = new Attrs();
		a.put("filter:", "\"(&(osgi.extender=osgi.component)(version>=1.3)(!(version>=2.0)))\"");
		p.put("osgi.extender", a);
		String s = p.toString();
		requires.add(s);
	}

	/*
	 * This method is a pass thru for the properties of the analyzer. If we have
	 * such a header, we get the analyzer header and concatenate our values
	 * after removing dups.
	 */

	public void updateHeader(Analyzer analyzer, String name, Set<String> set) {
		String value = analyzer.getProperty(name);
		if (!set.isEmpty()) {
			//
			// Remove duplicates and sort
			//
			String header = Strings.join(set);
			if (value == null)
				analyzer.setProperty(name, header);
			else
				analyzer.setProperty(name, value + "," + header);
		}
	}


	@Override
	public String toString() {
		return "DSAnnotations";
	}
}
