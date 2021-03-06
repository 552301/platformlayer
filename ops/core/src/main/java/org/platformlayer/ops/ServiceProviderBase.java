package org.platformlayer.ops;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.platformlayer.core.model.Action;
import org.platformlayer.core.model.Generate;
import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.Secret;
import org.platformlayer.core.model.ServiceInfo;
import org.platformlayer.ids.ItemType;
import org.platformlayer.ids.ServiceType;
import org.platformlayer.inject.ObjectInjector;
import org.platformlayer.metrics.model.MetricDataSource;
import org.platformlayer.metrics.model.MetricQuery;
import org.platformlayer.ops.crypto.Passwords;
import org.platformlayer.ops.helpers.ServiceContext;
import org.platformlayer.ops.helpers.SshKey;
import org.platformlayer.ops.metrics.MetricFetcher;
import org.platformlayer.xaas.Controller;
import org.platformlayer.xaas.Service;
import org.platformlayer.xaas.discovery.AnnotatedClass;
import org.platformlayer.xaas.discovery.AnnotationDiscovery;
import org.platformlayer.xaas.model.ServiceAuthorization;
import org.platformlayer.xaas.services.ModelClass;
import org.platformlayer.xaas.services.Models;
import org.platformlayer.xaas.services.ServiceProvider;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Injector;

public abstract class ServiceProviderBase implements ServiceProvider {
	private final ServiceType serviceType;

	private final String description;

	@Inject
	AnnotationDiscovery discovery;

	@Inject
	protected ObjectInjector injector;

	@Inject
	CloudContextRegistry cloudContextRegistry;

	public ServiceProviderBase() {
		Service serviceAnnotation = getServiceAnnotation();

		String key = serviceAnnotation.value();

		// this.serviceType = serviceType;
		this.description = key;
		this.serviceType = new ServiceType(key);
	}

	Models models;

	@Override
	public Models getModels() {
		if (models == null) {
			models = new Models(buildModels());
		}
		return models;
	}

	protected List<ModelClass<?>> buildModels() {
		List<ModelClass<?>> modelClasses = Lists.newArrayList();
		for (AnnotatedClass clazz : discovery.findAnnotatedClasses(Controller.class)) {
			ModelClass<?> modelClass = asModelClass((Class<? extends ItemBase>) clazz.getSubjectClass());
			if (modelClass != null) {
				modelClasses.add(modelClass);
			}
		}
		return modelClasses;
	}

	private Service getServiceAnnotation() {
		return getClass().getAnnotation(Service.class);
	}

	@Override
	public ServiceType getServiceType() {
		return serviceType;
	}

	@Override
	public ServiceInfo getServiceInfo() {
		ServiceInfo serviceInfo = new ServiceInfo();
		serviceInfo.serviceType = getServiceType().getKey();
		serviceInfo.description = description;
		// serviceInfo.schema =

		for (ModelClass<?> modelClass : getModels().all()) {
			ItemType itemType = modelClass.getItemType();

			if (serviceInfo.getNamespace() == null) {
				serviceInfo.namespace = modelClass.getPrimaryNamespace();
			}

			if (serviceInfo.itemTypes == null) {
				serviceInfo.itemTypes = Lists.newArrayList();
			}
			serviceInfo.itemTypes.add(itemType.getKey());
		}

		return serviceInfo;
	}

	@Override
	public void initialize() {
	}

	@Override
	public void beforeCreateItem(ItemBase item) throws OpsException {
		resolveKeys(item);

		autoPopulate(item);

		CreationValidator validator = Injection.getInstance(CreationValidator.class);
		validator.validateCreateItem(item);
	}

	@Override
	public void beforeDeleteItem(ItemBase managedItem) throws OpsException {
	}

	protected ModelClass<?> asModelClass(Class<? extends ItemBase> clazz) {
		Class<? extends ServiceProviderBase> myClass = getClass();
		Package myPackage = myClass.getPackage();

		Package clazzPackage = clazz.getPackage();

		// TODO: This is a bit hacky..
		String packagePrefix = myPackage.getName() + ".";
		if (!clazzPackage.getName().startsWith(packagePrefix)) {
			return null;
		}

		Controller modelAnnotation = clazz.getAnnotation(Controller.class);
		if (modelAnnotation == null) {
			return null;
		}

		return ModelClass.publicModel(this, clazz);
	}

	@Override
	public void validateAuthorization(ServiceAuthorization serviceAuthorization) throws OpsException {
		CloudContext cloudContext = cloudContextRegistry.getCloudContext();
		cloudContext.validate();
	}

	@Override
	public Object getController(Class<?> managedItemClass) throws OpsException {
		Class<?> controllerClass = getControllerClass(managedItemClass);

		ensureInitialized();

		return injector.getInstance(controllerClass);
	}

	@Override
	public Object getController(Object item) throws OpsException {
		Class<?> managedItemClass = item.getClass();

		Class<?> controllerClass = getControllerClass(managedItemClass);

		ensureInitialized();

		Object controller = injector.getInstance(controllerClass);

		Injector guiceInjector = injector.getInstance(Injector.class);

		Map<Class<?>, Object> prebound = Maps.newHashMap();
		prebound.put(item.getClass(), item);

		BindingHelper helper = new BindingHelper(guiceInjector, prebound);
		helper.bind(controller);

		return controller;
	}

	@Override
	public Class<?> getControllerClass(Class<?> managedItemClass) throws OpsException {
		Controller controller = managedItemClass.getAnnotation(Controller.class);
		if (controller == null) {
			throw new IllegalArgumentException("No @Controller annotation found for " + managedItemClass.getName());
		}

		ensureInitialized();

		return controller.value();
	}

	boolean initialized;

	protected void ensureInitialized() throws OpsException {
		if (initialized) {
			return;
		}

		ServiceInitializer initializer = injector.getInstance(ServiceInitializer.class);
		initializer.initialize(this);

		initialized = true;
	}

	@Override
	public MetricDataSource getMetricValues(ItemBase item, MetricQuery query) throws OpsException {
		MetricFetcher metricFetcher = injector.getInstance(MetricFetcher.class);
		return metricFetcher.fetch(this, item, query);
	}

	@Override
	public Class<?> getJavaClass(ItemType itemType) {
		ModelClass<?> modelClass = findModelClass(itemType);
		if (modelClass == null) {
			return null;
		}

		return modelClass.getJavaClass();
	}

	@Override
	public ModelClass<?> getModelClass(ItemType itemType) {
		ModelClass<?> modelClass = findModelClass(itemType);
		return modelClass;
	}

	private ModelClass<?> findModelClass(ItemType itemType) {
		for (ModelClass<?> modelClass : getModels().all()) {
			if (!itemType.equals(modelClass.getItemType())) {
				continue;
			}

			return modelClass;
		}
		return null;
	}

	@Override
	public PublicKey getSshPublicKey() throws OpsException {
		ServiceContext serviceContext = injector.getInstance(ServiceContext.class);

		SshKey sshKey = serviceContext.getSshKey();
		if (sshKey == null) {
			return null;
		}
		PublicKey publicKey = sshKey.getKeyPair().getPublic();
		return publicKey;
	}

	public void resolveKeys(Object item) throws OpsException {
		Class<? extends Object> itemClass = item.getClass();
		for (Field field : itemClass.getFields()) {
			Class<?> fieldType = field.getType();

			if (fieldType == PlatformLayerKey.class) {
				PlatformLayerKey key;
				try {
					key = (PlatformLayerKey) field.get(item);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("Error getting field: " + field, e);
				}

				if (key != null) {
					PlatformLayerKey newKey = resolveKey(key);

					if (newKey != key) {
						try {
							field.set(item, newKey);
						} catch (IllegalAccessException e) {
							throw new IllegalStateException("Error setting field: " + field, e);
						}
					}
				}
			}

			if (fieldType == List.class) {
				Type genericFieldType = field.getGenericType();

				if (genericFieldType instanceof ParameterizedType) {
					ParameterizedType aType = (ParameterizedType) genericFieldType;
					Type[] fieldArgTypes = aType.getActualTypeArguments();
					if (fieldArgTypes.length == 1) {
						Type fieldArgType = fieldArgTypes[0];
						if (fieldArgType instanceof Class) {
							Class fieldArg = (Class) fieldArgType;

							if (fieldArg.equals(PlatformLayerKey.class)) {
								List<PlatformLayerKey> list;
								try {
									list = (List<PlatformLayerKey>) field.get(item);
								} catch (IllegalAccessException e) {
									throw new IllegalStateException("Error getting field: " + field, e);
								}
								if (list != null) {
									for (int i = 0; i < list.size(); i++) {
										PlatformLayerKey key = list.get(i);
										PlatformLayerKey newKey = resolveKey(key);

										if (newKey != key) {
											list.set(i, newKey);
										}
									}

									try {
										field.set(item, list);
									} catch (IllegalAccessException e) {
										throw new IllegalStateException("Error setting field: " + field, e);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	private PlatformLayerKey resolveKey(PlatformLayerKey key) throws OpsException {
		if (key.getServiceType() == null) {
			ItemType itemType = key.getItemType();
			ServiceType serviceType = OpsContext.get().getOpsSystem().getServiceType(itemType);
			key = key.withServiceType(serviceType);
		}

		if (key.getProject() == null) {
			key = key.withProject(OpsContext.get().getPlatformLayerClient().getProject());
		}

		return key;
	}

	public void autoPopulate(Object item) throws OpsException {
		Class<? extends Object> itemClass = item.getClass();
		for (Field field : itemClass.getFields()) {
			Generate defaultAnnotation = field.getAnnotation(Generate.class);

			if (defaultAnnotation != null) {
				Class<?> fieldType = field.getType();

				Object value;
				try {
					value = field.get(item);
				} catch (IllegalAccessException e) {
					throw new IllegalStateException("Error getting field: " + field, e);
				}

				if (value == null) {
					String defaultValue = defaultAnnotation.value();
					if (!Strings.isNullOrEmpty(defaultValue)) {
						value = defaultValue;
					} else {
						if (fieldType == Secret.class) {
							Passwords passwords = new Passwords();

							Secret secret = passwords.generateRandomPassword(12);
							value = secret;

						}
					}

					if (value != null) {
						try {
							field.set(item, value);
						} catch (IllegalAccessException e) {
							throw new IllegalStateException("Error setting field: " + field, e);
						}
					}
				}
			}
		}
	}

	@Override
	public List<Class<? extends Action>> getActions() {
		return Collections.emptyList();
	}

	@Override
	public Object getExtensionResource() {
		return null;
	}

	@Override
	public Object getItemExtensionResource(Object item) throws OpsException {
		Object controller = getController(item);
		if (controller instanceof HasResource) {
			return ((HasResource) controller).getItemExtensionResource();
		}
		return null;
	}

	@Override
	public String buildItemId(ModelClass<?> modelClass, ItemBase item) {
		String id = modelClass.getItemType().getKey();
		return id;
	}
}
