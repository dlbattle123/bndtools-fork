package bndtools.core.test.utils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.bndtools.api.BndtoolsConstants;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.ICoreRunnable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.jdt.core.ClasspathContainerInitializer;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import aQute.bnd.build.Project;
import aQute.bnd.build.Workspace;
import aQute.bnd.build.model.BndEditModel;
import aQute.bnd.build.model.clauses.VersionedClause;
import aQute.bnd.exceptions.Exceptions;
import aQute.bnd.exceptions.SupplierWithException;
import aQute.bnd.osgi.Constants;
import bndtools.central.Central;

public class TaskUtils {

	static IResourceVisitor VISITOR = resource -> {
		IPath path = resource.getFullPath();
		log(path == null ? "null" : path.toString());
		return true;
	};

	private TaskUtils() {}

	public static void log(SupplierWithException<String> msg) {
		try {
			// System.err.println(System.currentTimeMillis() + ": " +
			// msg.get());
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public static void log(String msg) {
		// log(() -> msg);
	}

	public static void dumpWorkspace() {
		IWorkspace ws = ResourcesPlugin.getWorkspace();
		try {
			ws.getRoot()
				.accept(VISITOR);
		} catch (CoreException e) {
			throw Exceptions.duck(e);
		}
	}

	public static boolean setAutobuild(boolean on) {
		try {
			IWorkspace eclipse = ResourcesPlugin.getWorkspace();
			IWorkspaceDescription description = eclipse.getDescription();
			boolean original = description.isAutoBuilding();
			description.setAutoBuilding(on);
			eclipse.setDescription(description);
			return original;
		} catch (CoreException e) {
			throw Exceptions.duck(e);
		}
	}

	// Synchronously build the workspace
	public static void buildFull(String context) {
		try {
			ResourcesPlugin.getWorkspace()
				.build(IncrementalProjectBuilder.FULL_BUILD, new LoggingProgressMonitor(context));
		} catch (CoreException e) {
			throw Exceptions.duck(e);
		}
	}

	// Synchronously build the workspace
	public static void buildClean(String context) {
		try {
			ResourcesPlugin.getWorkspace()
				.build(IncrementalProjectBuilder.CLEAN_BUILD, new LoggingProgressMonitor(context));
		} catch (CoreException e) {
			throw Exceptions.duck(e);
		}
	}

	public static void buildIncremental(String context) {
		try {
			ResourcesPlugin.getWorkspace()
				.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new LoggingProgressMonitor(context));
		} catch (CoreException e) {
			throw Exceptions.duck(e);
		}
	}

	public static void updateWorkspace(String context) throws Exception {
		log(context + ": Updating Bnd workspace");
		Workspace bndWs = Central.getWorkspace();
		bndWs.clear();
		bndWs.forceRefresh();
		bndWs.getPlugins();
	}

	public static void requestClasspathUpdate(String context) {
		try {
			log(context + ": Initiating classpath update");
			ClasspathContainerInitializer initializer = JavaCore
				.getClasspathContainerInitializer(BndtoolsConstants.BND_CLASSPATH_ID.segment(0));
			if (initializer != null) {
				IJavaModel javaModel = JavaCore.create(ResourcesPlugin.getWorkspace()
					.getRoot());

				for (IJavaProject project : javaModel.getJavaProjects()) {
					log(() -> context + ": Updating classpath for " + project.getElementName());
					initializer.requestClasspathContainerUpdate(BndtoolsConstants.BND_CLASSPATH_ID, project, null);
				}
			}
		} catch (CoreException e) {
			throw Exceptions.duck(e);
		}
		log(context + ": done classpath update");
	}

	public static void waitForBuild(String context) {
		log(context + ": Initiating build");
		buildIncremental(context);
		log(context + ": done waiting for build to complete");
	}

	public static void doAutobuildAndWait(String context) {
		log(context + ": turning on autobuild");
		setAutobuild(true);
		waitForAutobuild(context);
		log(context + ": turning off autobuild");
		setAutobuild(false);
	}

	/**
	 * Waits for autobuild to finish. Usually, you'll want to use
	 * {@link #waitForAutobuild(ICoreRunnable, String)} to avoid timing issues,
	 * otherwise this method can return immediately in the small window before
	 * autobuild starts.
	 *
	 * @param context
	 */
	public static void waitForAutobuild(String context) {
		final IJobManager jm = Job.getJobManager();
		try {
			// Wait for manual build to finish if running
			jm.join(ResourcesPlugin.FAMILY_MANUAL_BUILD,
				new LoggingProgressMonitor(context + ": Waiting for manual build"));
			// Wait for auto build to finish if running
			jm.join(ResourcesPlugin.FAMILY_AUTO_BUILD, new LoggingProgressMonitor(context + ": Waiting for autobuild"));
		} catch (InterruptedException e) {
			throw Exceptions.duck(e);
		}
	}

	public static boolean isAutobuild(Job job) {
		return job.belongsTo(ResourcesPlugin.FAMILY_AUTO_BUILD);
	}

	public static void waitForAutobuild(ICoreRunnable modTask, String context) {

		final IJobManager jm = Job.getJobManager();
		final CountDownLatch startFlag = new CountDownLatch(1);
		final CountDownLatch endFlag = new CountDownLatch(1);
		IJobChangeListener l = new JobChangeAdapter() {
			@Override
			public void running(IJobChangeEvent event) {
				if (isAutobuild(event.getJob())) {
					log(context + ": autobuild starting: " + event.getJob());
					startFlag.countDown();
					jm.removeJobChangeListener(this);
				}
			}
		};
		try {
			ResourcesPlugin.getWorkspace()
				.run(monitor -> {
					log(context + ": adding autobuild listener");
					jm.addJobChangeListener(l);
					log(context + ": autobuild listener added, running task");
					modTask.run(monitor);
				}, new LoggingProgressMonitor(context));
			if (startFlag.await(10000, TimeUnit.MILLISECONDS) == false) {
				throw new IllegalStateException("Autobuild didn't start within 10s: " + context);
			}
			waitForAutobuild(context);
		} catch (Exception e) {
			throw Exceptions.duck(e);
		} finally {
			jm.removeJobChangeListener(l);
		}
		log(context + ": finished");
	}

	public static void addBundlesToBuildpath(Project bndProject, String... bundleNames) {
		try {
			BndEditModel model = new BndEditModel(bndProject);
			model.load();

			for (String bundleName : bundleNames) {
				model.addPath(new VersionedClause(bundleName, null), Constants.BUILDPATH);
			}
			model.saveChanges();
			Central.refresh(bndProject);
			requestClasspathUpdate("addBundleToBuildpath()");
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}

	public static boolean clearBuildpath(Project bndProject) {
		log("clearing buildpath");
		try {
			BndEditModel model = new BndEditModel(bndProject);
			model.load();
			List<VersionedClause> buildPath = model.getBuildPath();
			if (buildPath != null && !buildPath.isEmpty()) {
				model.setBuildPath(Collections.emptyList());
				model.saveChanges();
				Central.refresh(bndProject);
				TaskUtils.requestClasspathUpdate("clearBuildpath()");
				return true;
			} else {
				log("buildpath was not set; not trying to clear it");
				return false;
			}
		} catch (Exception e) {
			throw Exceptions.duck(e);
		}
	}
}
