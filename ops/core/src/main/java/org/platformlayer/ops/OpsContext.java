package org.platformlayer.ops;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;

import javalang7.AutoCloseable;
import javalang7.Utils;

import org.platformlayer.ApplicationMode;
import org.platformlayer.CheckedCallable;
import org.platformlayer.PlatformLayerClient;
import org.platformlayer.Scope;
import org.platformlayer.core.model.Action;
import org.platformlayer.core.model.ConfigureAction;
import org.platformlayer.core.model.DeleteAction;
import org.platformlayer.core.model.ValidateAction;
import org.platformlayer.model.ProjectAuthorization;
import org.platformlayer.ops.log.JobLogger;
import org.platformlayer.ops.model.metrics.MetricConfig;
import org.platformlayer.ops.networks.NetworkPoint;
import org.platformlayer.ops.tasks.ActiveJobExecution;
import org.platformlayer.ops.tasks.OperationInvoker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fathomdb.Configuration;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Injector;

public class OpsContext implements Closeable {
	static final Logger log = LoggerFactory.getLogger(OpsContext.class);

	final OpsSystem opsSystem;
	final List<AutoCloseable> ownedObjects = Lists.newArrayList();

	final ServiceConfiguration serviceConfiguration;

	boolean isFailure = false;

	final JobLogger jobLogger;

	final ActiveJobExecution jobRecord;

	final Map<Object, Object> cacheMap = Maps.newHashMap();

	final PlatformLayerClient platformLayerClient;

	final List<ProjectAuthorization> projects;

	public OpsContext(OpsSystem opsSystem, ActiveJobExecution jobRecord, ServiceConfiguration serviceConfiguration,
			PlatformLayerClient platformLayerClient, List<ProjectAuthorization> projects) {
		super();
		this.opsSystem = opsSystem;
		this.jobRecord = jobRecord;
		this.serviceConfiguration = serviceConfiguration;
		this.jobLogger = jobRecord.getLogger();
		this.platformLayerClient = platformLayerClient;
		this.projects = projects;
	}

	public static OpsContext get() {
		Scope scope = Scope.get();
		if (scope == null) {
			return null;
		}
		return scope.get(OpsContext.class);
	}

	public <T> T getInstance(Class<T> clazz) {
		BindingScope scope = BindingScope.get();
		if (scope == null) {
			return null;
		}
		return scope.getInstance(clazz);
	}

	public static <T, E extends Exception> T runInContext(OpsContext opsContext, CheckedCallable<T, E> callable)
			throws OpsException {
		Scope scope = Scope.inherit();
		scope.put(opsContext);
		try {
			scope.push();
			return callable.call();
		} catch (Exception e) {
			log.warn("Error running operation", e);
			if (e instanceof OpsException) {
				throw ((OpsException) e);
			}
			throw new OpsException("Error during operation", e);
		} finally {
			try {
				Utils.safeClose(opsContext);
			} catch (Exception e) {
				log.error("Error while closing OpsContext");
			}

			scope.pop();
		}
	}

	public Configuration getConfiguration() {
		return getOpsSystem().getConfiguration();
	}

	public OpsSystem getOpsSystem() {
		return opsSystem;
	}

	public void takeOwnership(AutoCloseable closeable) {
		ownedObjects.add(closeable);
	}

	@Override
	public void close() throws IOException {
		for (AutoCloseable closeable : ownedObjects) {
			Utils.safeClose(closeable);
		}
	}

	public OpaqueMachine connectToMachine(String dnsName) throws OpsException {
		return new OpaqueMachine(NetworkPoint.forPublicHostname(dnsName));
	}

	public ServiceConfiguration getServiceConfiguration() {
		return serviceConfiguration;
	}

	public void takeOwnership(final Machine machine) {
		takeOwnership(new AutoCloseable() {
			@Override
			public void close() throws Exception {
				if (isFailure() && ApplicationMode.isDevelopment()) {
					log.warn("FAILURE IN DEBUG MODE: will not shutdown machine");
					return;
				}
				machine.terminate();
			}
		});
	}

	public boolean isFailure() {
		return isFailure;
	}

	public void setFailure(boolean isFailure) {
		this.isFailure = isFailure;
	}

	public void addWarning(Object item, String format, Object... parameters) {
		// TODO: Implement addWarning
		log.warn("Stubbed-out: addWarning");
	}

	public void recurseOperation(Object target) throws OpsException {
		enterLogScope(target);
		try {
			if (target instanceof OpsItem) {
				if (((OpsItem) target).isLazyDelete() && OpsContext.isDelete()) {
					log.debug("Skipping recursion - lazy delete");
					return;
				}
			}

			OperationInvoker operationInvoker = opsSystem.getInjector().getInstance(OperationInvoker.class);

			try {
				operationInvoker.invoke(target);
			} catch (IllegalArgumentException e) {
				throw new OpsException("Error invoking method on " + target.getClass(), e);
			} catch (IllegalAccessException e) {
				throw new OpsException("Error invoking method on " + target.getClass(), e);
			} catch (InvocationTargetException e) {
				throw new OpsException("Error invoking method on " + target.getClass(), e);
			}

			OperationRecursor.doRecurseOperation(this, target);
		} finally {
			exitLogScope();
		}
	}

	private void enterLogScope(Object controller) {
		OpsContext opsContext = OpsContext.get();

		if (opsContext != null) {
			JobLogger jobLogger = opsContext.getJobLogger();
			jobLogger.enterScope(controller);
		}
	}

	private void exitLogScope() {
		OpsContext opsContext = OpsContext.get();

		if (opsContext != null) {
			JobLogger jobLogger = opsContext.getJobLogger();
			jobLogger.exitScope();
		}
	}

	public MetricConfig getMetricInfo(Object target) throws OpsException {
		MetricCollector metricCollector = opsSystem.getInjector().getInstance(MetricCollector.class);
		return metricCollector.getMetricInfo(target);
	}

	public void recurseOperation(Object... targets) throws OpsException {
		for (Object target : targets) {
			recurseOperation(target);
		}
	}

	public void recurseOperation(Iterable<Object> targets) throws OpsException {
		for (Object target : targets) {
			recurseOperation(target);
		}
	}

	public void recurseOperation(BindingScope scope, Object... targets) throws OpsException {
		try {
			recurseOperation(targets);
		} finally {
			scope.pop();
		}
	}

	public Injector getInjector() {
		return opsSystem.getInjector();
	}

	public ActiveJobExecution getJobRecord() {
		return jobRecord;
	}

	public Action getAction() {
		return getInstance(Action.class);
	}

	public JobLogger getJobLogger() {
		return jobLogger;
	}

	public static boolean isDelete() {
		return isOperationType(DeleteAction.class);
	}

	public static boolean isOperationType(Class<? extends Action> actionClass) {
		Action action = OpsContext.get().getAction();
		return actionClass.isInstance(action);
	}

	public static boolean isConfigure() {
		return isOperationType(ConfigureAction.class);
	}

	public static boolean isValidate() {
		return isOperationType(ValidateAction.class);
	}

	public Map<Object, Object> getCacheMap() {
		return cacheMap;
	}

	public PlatformLayerClient getPlatformLayerClient() throws OpsException {
		return platformLayerClient;
	}

	public List<ProjectAuthorization> getEncryptingProjects() {
		return projects;
	}

	public static boolean isForce() {
		// TODO: Support force actions??
		return false;
	}

	public static <T> T injected(Class<T> clazz, Object... preboundItems) {
		Injector injector = OpsContext.get().getInjector();

		Map<Class<?>, Object> prebound = Maps.newHashMap();
		for (Object preboundItem : preboundItems) {
			prebound.put(preboundItem.getClass(), preboundItem);
		}

		BindingHelper helper = new BindingHelper(injector, prebound);

		T item = injector.getInstance(clazz);
		helper.bind(item);

		return item;
	}

	// public OpsProject getProject() {
	// return auth.getProject();
	// }
	//
	// public ProjectId getProjectId() {
	// OpsProject project = getProject();
	// if (project == null) {
	// return null;
	// }
	// return new ProjectId(project.key);
	// }
}
