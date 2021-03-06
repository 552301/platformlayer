package org.platformlayer.guice.xaas;

import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.platformlayer.Filter;
import org.platformlayer.RepositoryException;
import org.platformlayer.auth.crypto.SecretProvider;
import org.platformlayer.core.model.ItemBase;
import org.platformlayer.core.model.ManagedItemState;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.SecretInfo;
import org.platformlayer.core.model.Tag;
import org.platformlayer.core.model.TagChanges;
import org.platformlayer.core.model.Tags;
import org.platformlayer.ids.ItemType;
import org.platformlayer.ids.ManagedItemId;
import org.platformlayer.ids.ModelKey;
import org.platformlayer.ids.ProjectId;
import org.platformlayer.ids.ServiceType;
import org.platformlayer.jdbc.DbHelperBase;
import org.platformlayer.ops.crypto.SecretHelper;
import org.platformlayer.xaas.repository.ManagedItemRepository;
import org.platformlayer.xaas.services.ModelClass;
import org.platformlayer.xaas.services.ServiceProvider;
import org.platformlayer.xaas.services.ServiceProviderDictionary;
import org.platformlayer.xml.JaxbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fathomdb.Utf8;
import com.fathomdb.crypto.AesCryptoKey;
import com.fathomdb.crypto.CryptoKey;
import com.fathomdb.crypto.FathomdbCrypto;
import com.fathomdb.jdbc.JdbcConnection;
import com.fathomdb.jdbc.JdbcTransaction;
import com.fathomdb.jdbc.JdbcUtils;
import com.fathomdb.jpa.Query;
import com.fathomdb.jpa.QueryFactory;
import com.fathomdb.jpa.QueryFilter;
import com.fathomdb.jpa.impl.JoinedQueryResult;
import com.google.common.base.Charsets;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

public class JdbcManagedItemRepository implements ManagedItemRepository {

	private static final Logger log = LoggerFactory.getLogger(JdbcManagedItemRepository.class);

	/**
	 * We originally weren't de-duplicating tags, but I think we want to
	 */
	static final boolean REMOVE_DUPLICATE_TAGS = true;

	@Inject
	ServiceProviderDictionary serviceProviderDirectory;

	@Inject
	Provider<JdbcConnection> connectionProvider;

	@Inject
	SecretHelper itemSecrets;

	@Override
	@JdbcTransaction
	public <T extends ItemBase> List<T> findAll(ModelClass<T> modelClass, ProjectId project, boolean fetchTags,
			SecretProvider secretProvider, Filter filter) throws RepositoryException {
		DbHelper db = new DbHelper(modelClass, project);

		try {
			int projectId = db.mapToValue(project);
			int modelId = db.mapToValue(modelClass.getItemType());
			int serviceId = db.mapToValue(modelClass.getServiceType());

			String filterKey = null;

			JoinedQueryResult result = db.queries.listItems(serviceId, modelId, projectId, filterKey);

			List<T> items = mapItemsAndTags(project, secretProvider, db, result);
			return applyFilter(items, filter);
		} catch (SQLException e) {
			throw new RepositoryException("Error fetching items", e);
		} finally {
			db.close();
		}
	}

	private <T extends ItemBase> List<T> mapItemsAndTags(ProjectId project, SecretProvider secretProvider, DbHelper db,
			JoinedQueryResult result) throws RepositoryException, SQLException {
		Multimap<Integer, Tag> itemTags = HashMultimap.create();
		for (TagEntity row : result.getAll(TagEntity.class)) {
			Tag tag = Tag.build(row.key, row.data);
			itemTags.put(row.item, tag);
		}

		List<T> items = Lists.newArrayList();

		for (ItemEntity entity : result.getAll(ItemEntity.class)) {
			if (entity == null) {
				throw new IllegalStateException();
			}

			ServiceType serviceType = db.getServiceType(entity.service);
			ItemType itemType = db.getItemType(entity.model);

			JaxbHelper jaxbHelper = getJaxbHelper(db, serviceType, itemType);
			T item = mapToModel(project, serviceType, itemType, entity, jaxbHelper, secretProvider);

			int itemId = entity.id;
			Collection<Tag> tags = itemTags.get(itemId);
			item.getTags().addAll(tags);

			items.add(item);
		}

		return items;
	}

	@Override
	@JdbcTransaction
	public List<ItemBase> findRoots(ProjectId project, boolean fetchTags, SecretProvider secretProvider)
			throws RepositoryException {
		DbHelper db = new DbHelper(project);

		try {
			// TODO: Push-down logic for item selection as well

			JoinedQueryResult result = db.listRoots();

			List<ItemBase> roots = mapItemsAndTags(project, secretProvider, db, result);

			for (ItemBase root : roots) {
				// A little bit of paranoia
				boolean isRoot = true;
				for (Tag tag : root.getTags()) {
					boolean tagIsParent = Tag.PARENT.getKey().equals(tag.getKey());
					if (tagIsParent) {
						isRoot = false;
						break;
					}
				}

				assert isRoot;

				if (!isRoot) {
					throw new IllegalStateException();
				}
			}

			return roots;
		} catch (SQLException e) {
			throw new RepositoryException("Error fetching items", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public List<ItemBase> listAll(ProjectId project, Filter filter, SecretProvider secretProvider)
			throws RepositoryException {
		DbHelper db = new DbHelper(project);

		try {
			log.debug("listAll with filter: {}", filter);

			// TODO: Use this logic for item selection as well

			List<Tag> requiredTags = filter.getRequiredTags();

			JoinedQueryResult result;
			if (!requiredTags.isEmpty()) {
				Tag requiredTag = requiredTags.get(0);

				int projectId = db.mapToValue(project);
				result = db.queries.listAllItemsWithTag(projectId, projectId, requiredTag.getKey(),
						requiredTag.getValue());
			} else {
				log.warn("Unable to optimize filter; selecting all items.  Filter={}", filter);
				result = db.listAllItems();
			}

			List<ItemBase> items = mapItemsAndTags(project, secretProvider, db, result);

			return applyFilter(items, filter);
		} catch (SQLException e) {
			throw new RepositoryException("Error fetching items", e);
		} finally {
			db.close();
		}
	}

	private <T extends ItemBase> List<T> applyFilter(List<T> items, Filter filter) {
		if (filter == null) {
			return items;
		}

		List<T> matching = Lists.newArrayList();

		for (T item : items) {
			if (filter.matchesItem(item)) {
				matching.add(item);
			}
		}

		return matching;
	}

	private JaxbHelper getJaxbHelper(DbHelper db, ServiceType serviceType, ItemType itemType) throws SQLException {
		if (serviceType == null || itemType == null) {
			throw new IllegalStateException();
		}

		ServiceProvider serviceProvider = serviceProviderDirectory.getServiceProvider(serviceType);
		if (serviceProvider == null) {
			throw new IllegalStateException("Cannot find service provider: " + serviceType);
		}

		ModelClass<?> modelClass = serviceProvider.getModelClass(itemType);
		if (modelClass == null) {
			throw new IllegalStateException();
		}

		JaxbHelper jaxbHelper = JaxbHelper.get(modelClass.getJavaClass());
		return jaxbHelper;
	}

	static <T extends ItemBase> T mapToModel(ProjectId project, ServiceType serviceType, ItemType itemType,
			ItemEntity entity, JaxbHelper jaxb, SecretProvider secretProvider) throws RepositoryException {
		try {
			int id = entity.id;
			String key = entity.key;
			int stateCode = entity.state;
			byte[] data = entity.data;

			SecretInfo secret = new SecretInfo(entity.secret);
			CryptoKey itemSecret = secretProvider.getItemSecret(secret);

			if (itemSecret == null) {
				throw new RepositoryException("Could not get secret to decrypt item");
			}

			if (itemSecret instanceof AesCryptoKey) {
				log.warn("Legacy AES crypto key on {} {} {} {}",
						new Object[] { project, serviceType, itemType, entity });
			}

			secret.unlock(itemSecret);

			byte[] plaintext = FathomdbCrypto.decrypt(itemSecret, data);
			String xml = new String(plaintext, Charsets.UTF_8);

			T model = (T) jaxb.unmarshal(xml);

			model.state = ManagedItemState.fromCode(stateCode);

			model.secret = secret;

			PlatformLayerKey plk = new PlatformLayerKey(null, project, serviceType, itemType, new ManagedItemId(key));
			model.setKey(plk);

			return model;
		} catch (JAXBException e) {
			throw new RepositoryException("Error deserializing data", e);
		}
	}

	static interface Queries {
		@Query("SELECT i.* FROM items i WHERE i.service=? and i.model=? and i.project=?")
		ItemEntity findItem(int serviceId, int modelId, int projectId, @QueryFilter("i.key=?") String itemKey)
				throws SQLException;

		@Query("SELECT i.*, t.* FROM items i LEFT JOIN item_tags t on t.item = i.id WHERE i.service=? and i.model=? and i.project=?")
		JoinedQueryResult listItems(int serviceId, int modelId, int projectId, @QueryFilter("i.key=?") String itemKey)
				throws SQLException;

		@Query("SELECT i.*, t.* FROM items i LEFT JOIN item_tags t on t.item = i.id WHERE i.project=?")
		JoinedQueryResult listAllItems(int projectId) throws SQLException;

		@Query("SELECT i.*, t.* FROM items i LEFT JOIN item_tags t on t.item = i.id WHERE i.project=? and i.id IN (SELECT item from item_tags where project=? and key=? and data=?)")
		JoinedQueryResult listAllItemsWithTag(int projectId, int projectId2, String tagName, String tagValue)
				throws SQLException;

		@Query("SELECT i.*, t.* FROM items i LEFT JOIN item_tags t on t.item = i.id WHERE i.project=? and i.id NOT IN (SELECT item from item_tags WHERE project=? and key=?)")
		JoinedQueryResult listRoots(int projectId, int projectId2, String parentTag) throws SQLException;

		@Query("UPDATE items set secret=? where service=? and model=? and project=? and key=?")
		int updateSecret(byte[] itemSecret, int serviceId, int itemId, int projectId, String key);

		@Query("UPDATE items SET data=?, state=? WHERE service=? and model=? and project=? and key=?")
		int updateItem(byte[] data, int newState, int serviceId, int itemTypeId, int projectId, String itemKey);

		@Query("SELECT item, key, data FROM item_tags WHERE service=? and model=? and project=?")
		List<TagEntity> listTags(int serviceId, int modelId, int projectId) throws SQLException;

		@Query("SELECT item, key, data FROM item_tags WHERE project=?")
		List<TagEntity> listAllProjectTags(int projectId) throws SQLException;

		@Query("SELECT key, data FROM item_tags where service=? and model=? and project=? and item=?")
		List<TagEntity> listTagsForItem(int serviceId, int modelId, int projectId, int itemId);
	}

	@Inject
	QueryFactory queryFactory;

	class DbHelper extends DbHelperBase {
		final Queries queries;

		public DbHelper(ModelKey key) {
			this(key.getServiceType(), key.getItemType(), key.getProject());
		}

		public ItemType getItemType(int code) throws SQLException {
			String v = mapCodeToKey(ItemType.class, code);
			if (v == null) {
				return null;
			}
			return new ItemType(v);
		}

		public ServiceType getServiceType(int code) throws SQLException {
			String v = mapCodeToKey(ServiceType.class, code);
			if (v == null) {
				return null;
			}
			return new ServiceType(v);
		}

		public DbHelper(PlatformLayerKey key) {
			this(key.getServiceType(), key.getItemType(), key.getProject());
		}

		public DbHelper(ServiceType serviceType, ItemType itemType, ProjectId project) {
			super(connectionProvider.get());
			if (serviceType != null) {
				setAtom(serviceType);
			}
			if (itemType != null) {
				setAtom(itemType);
			}

			setAtom(project);

			this.queries = queryFactory.get(Queries.class);
		}

		public DbHelper(Class<? extends ItemBase> itemClass, ProjectId project) {
			this(serviceProviderDirectory.getModelClass(itemClass), project);
		}

		public DbHelper(ProjectId project) {
			this(null, null, project);
		}

		public DbHelper(ModelClass<?> modelClass, ProjectId project) {
			this(modelClass.getServiceType(), modelClass.getItemType(), project);
		}

		// public ItemEntity findByKey(ManagedItemId managedItemId) throws SQLException {
		// return queries.findByKey(getAtomValue(ServiceType.class), getAtomValue(ItemType.class),
		// getAtomValue(ProjectId.class), managedItemId.getKey());
		// }

		// public List<ItemEntity> listItems() throws SQLException {
		// return queries.listItems(getAtomValue(ServiceType.class), getAtomValue(ItemType.class),
		// getAtomValue(ProjectId.class));
		// }

		public JoinedQueryResult listAllItems() throws SQLException {
			return queries.listAllItems(getAtomValue(ProjectId.class));
		}

		public JoinedQueryResult listRoots() throws SQLException {
			int projectId = getAtomValue(ProjectId.class);
			return queries.listRoots(projectId, projectId, Tag.PARENT.getKey());
		}

		public List<TagEntity> listTags() throws SQLException {
			return queries.listTags(getAtomValue(ServiceType.class), getAtomValue(ItemType.class),
					getAtomValue(ProjectId.class));
		}

		public List<TagEntity> listTagsForItem(int itemId) throws SQLException {
			// TODO: We could do this using a join, or two statements with
			// one round-trip

			return queries.listTagsForItem(getAtomValue(ServiceType.class), getAtomValue(ItemType.class),
					getAtomValue(ProjectId.class), itemId);
		}

		public void insertTags(int itemId, Tags tags) throws SQLException {
			for (Tag tag : tags.getTags()) {
				insertTag(itemId, tag);
			}
		}

		public void insertTag(int itemId, Tag tag) throws SQLException {
			final String sql = "INSERT INTO item_tags (service, model, project, item, key, data) VALUES (?, ?, ?, ?, ?, ?)";

			PreparedStatement ps = prepareStatement(sql);
			setAtom(ps, 1, ServiceType.class);
			setAtom(ps, 2, ItemType.class);
			setAtom(ps, 3, ProjectId.class);
			ps.setInt(4, itemId);

			ps.setString(5, tag.getKey());
			ps.setString(6, tag.getValue());

			int updateCount = ps.executeUpdate();
			if (updateCount != 1) {
				throw new IllegalStateException("Unexpected number of rows inserted");
			}
		}

		public void removeTags(int itemId, Tags tags) throws SQLException {
			for (Tag tag : tags) {
				removeTag(itemId, tag);
			}
		}

		public void removeTag(int itemId, Tag tag) throws SQLException {
			PreparedStatement ps;
			if (tag.getValue() != null) {
				final String sql = "DELETE FROM item_tags WHERE service = ? and model=? and project=? and item=? and key=? and data=?";

				ps = prepareStatement(sql);
				setAtom(ps, 1, ServiceType.class);
				setAtom(ps, 2, ItemType.class);
				setAtom(ps, 3, ProjectId.class);
				ps.setInt(4, itemId);

				ps.setString(5, tag.getKey());
				ps.setString(6, tag.getValue());
			} else {
				final String sql = "DELETE FROM item_tags WHERE service = ? and model=? and project=? and item=? and key=? and data is null";

				ps = prepareStatement(sql);
				setAtom(ps, 1, ServiceType.class);
				setAtom(ps, 2, ItemType.class);
				setAtom(ps, 3, ProjectId.class);
				ps.setInt(4, itemId);

				ps.setString(5, tag.getKey());
			}
			ps.executeUpdate();
		}

		public int insertItem(ItemBase item, byte[] data, byte[] secretData) throws SQLException {

			Integer itemId = null;
			final String sql = "INSERT INTO items (service, model, project, state, data, key, secret) VALUES (?, ?, ?, ?, ?, ?, ?)";

			PreparedStatement ps = getJdbcConnection().prepareStatement(sql, new String[] { "id" });
			ResultSet rs = null;
			try {
				ManagedItemState managedItemState = item.state;

				setAtom(ps, 1, ServiceType.class);
				setAtom(ps, 2, ItemType.class);
				setAtom(ps, 3, ProjectId.class);
				ps.setInt(4, managedItemState.getCode());
				ps.setBytes(5, data);
				ps.setString(6, item.getId());
				ps.setBytes(7, secretData);

				int updateCount = ps.executeUpdate();
				if (updateCount != 1) {
					throw new IllegalStateException("Unexpected number of rows inserted");
				}

				rs = ps.getGeneratedKeys();
				while (rs.next()) {
					if (itemId != null) {
						throw new IllegalStateException();
					}

					itemId = rs.getInt(1);
				}
			} finally {
				JdbcUtils.safeClose(rs);
				JdbcUtils.safeClose(ps);
			}

			if (itemId == null) {
				throw new IllegalStateException();
			}
			return itemId;
		}

		public void updateSecret(ManagedItemId itemKey, byte[] itemSecret) throws SQLException {
			int updateCount = queries.updateSecret(itemSecret, getAtomValue(ServiceType.class),
					getAtomValue(ItemType.class), getAtomValue(ProjectId.class), itemKey.getKey());
			if (updateCount != 1) {
				throw new IllegalStateException("Unexpected number of rows inserted");
			}
		}

		public void updateItemState(ManagedItemState newState, ManagedItemId itemId) throws SQLException {
			final String sql = "UPDATE items set state=? where service=? and model=? and project=? and key=?";

			PreparedStatement ps = prepareStatement(sql);
			ps.setInt(1, newState.getCode());

			setAtom(ps, 2, ServiceType.class);
			setAtom(ps, 3, ItemType.class);
			setAtom(ps, 4, ProjectId.class);
			ps.setString(5, itemId.getKey());

			int updateCount = ps.executeUpdate();
			if (updateCount != 1) {
				throw new IllegalStateException("Unexpected number of rows updated");
			}
		}

		public void updateItem(ManagedItemId itemKey, byte[] data, ManagedItemState newState) throws SQLException {
			int updateCount = queries.updateItem(data, newState.getCode(), getAtomValue(ServiceType.class),
					getAtomValue(ItemType.class), getAtomValue(ProjectId.class), itemKey.getKey());
			if (updateCount != 1) {
				throw new IllegalStateException("Unexpected number of rows inserted");
			}
		}

		public int getProjectCode() throws SQLException {
			return getAtomValue(ProjectId.class);
		}
	}

	@Override
	@JdbcTransaction
	public ItemBase getManagedItem(PlatformLayerKey key, boolean fetchTags, SecretProvider secretProvider)
			throws RepositoryException {
		DbHelper db = new DbHelper(key);

		try {
			ServiceProvider serviceProvider = serviceProviderDirectory.getServiceProvider(key.getServiceType());
			if (serviceProvider == null) {
				throw new IllegalStateException();
			}

			ModelClass<?> modelClass = serviceProvider.getModelClass(key.getItemType());

			ServiceType serviceType = key.getServiceType();
			ItemType itemType = key.getItemType();
			ProjectId project = key.getProject();
			ManagedItemId itemId = key.getItemId();

			return fetchItem(db, serviceType, itemType, project, itemId, modelClass.getJavaClass(), secretProvider,
					fetchTags);
		} catch (SQLException e) {
			throw new RepositoryException("Error running query", e);
		} finally {
			db.close();
		}
	}

	private <T extends ItemBase> T fetchItem(DbHelper db, ServiceType serviceType, ItemType itemType,
			ProjectId project, ManagedItemId itemId, Class<T> modelClass, SecretProvider secretProvider,
			boolean fetchTags) throws SQLException, RepositoryException {

		int projectId = db.mapToValue(project);
		int modelId = db.mapToValue(itemType);
		int serviceId = db.mapToValue(serviceType);

		String filterKey = itemId.getKey();

		JoinedQueryResult result = db.queries.listItems(serviceId, modelId, projectId, filterKey);

		List<T> items = mapItemsAndTags(project, secretProvider, db, result);

		if (items.size() == 0) {
			return null;
		}

		if (items.size() != 1) {
			throw new IllegalStateException();
		}

		return items.get(0);
	}

	@Override
	@JdbcTransaction
	public <T extends ItemBase> T createManagedItem(ProjectId project, T item) throws RepositoryException {
		DbHelper db = new DbHelper(item.getClass(), project);
		try {
			CryptoKey itemSecret = FathomdbCrypto.generateKey();

			byte[] data = serialize(item, itemSecret);
			byte[] secretData = itemSecrets.encodeItemSecret(itemSecret);

			int itemId = db.insertItem(item, data, secretData);

			Tags tags = item.tags;
			if (tags != null && !tags.isEmpty()) {
				db.insertTags(itemId, tags);
			}

			return item;
		} catch (SQLException e) {
			throw new RepositoryException("Error running query", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public <T extends ItemBase> T updateManagedItem(ProjectId project, T item) throws RepositoryException {
		Class<T> itemClass = (Class<T>) item.getClass();

		DbHelper db = new DbHelper(itemClass, project);

		try {
			ManagedItemId itemId = new ManagedItemId(item.getId());

			ModelClass<T> modelClass = serviceProviderDirectory.getModelClass(itemClass);

			int projectId = db.mapToValue(project);
			int modelId = db.mapToValue(modelClass.getItemType());
			int serviceId = db.mapToValue(modelClass.getServiceType());

			ItemEntity rs = db.queries.findItem(serviceId, modelId, projectId, itemId.getKey());
			if (rs == null) {
				throw new RepositoryException("Item not found");
			}

			byte[] secretData = rs.secret;

			CryptoKey itemSecret;

			if (secretData == null) {
				itemSecret = FathomdbCrypto.generateKey();
				secretData = itemSecrets.encodeItemSecret(itemSecret);

				db.updateSecret(itemId, secretData);
			} else {
				itemSecret = item.secret.getSecret();
			}

			byte[] data = serialize(item, itemSecret);

			db.updateItem(itemId, data, item.state);

			// Note: we can't change tags here (that needs a separate call to updateTags)

			SecretProvider secretProvider = SecretProvider.forKey(itemSecret);

			boolean fetchTags = true;
			return fetchItem(db, modelClass.getServiceType(), modelClass.getItemType(), project, itemId, itemClass,
					secretProvider, fetchTags);
		} catch (SQLException e) {
			throw new RepositoryException("Error running query", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public void changeState(PlatformLayerKey key, ManagedItemState newState) throws RepositoryException {
		DbHelper db = new DbHelper(key);

		try {
			db.updateItemState(newState, key.getItemId());
		} catch (SQLException e) {
			throw new RepositoryException("Error running query", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public int getProjectCode(ProjectId project) throws RepositoryException {
		DbHelper db = new DbHelper(project);

		try {
			int atomValue = db.getProjectCode();
			return atomValue;
		} catch (SQLException e) {
			throw new RepositoryException("Error running query", e);
		} finally {
			db.close();
		}

	}

	@Override
	@JdbcTransaction
	public Tags changeTags(ModelClass<?> modelClass, ProjectId project, ManagedItemId itemKey, TagChanges changeTags,
			Long ifVersion) throws RepositoryException {
		DbHelper db = new DbHelper(modelClass, project);

		try {
			int projectId = db.mapToValue(project);
			int modelId = db.mapToValue(modelClass.getItemType());
			int serviceId = db.mapToValue(modelClass.getServiceType());

			ItemEntity rs = db.queries.findItem(serviceId, modelId, projectId, itemKey.getKey());
			if (rs == null) {
				// TODO: Better exception??
				throw new IllegalStateException("Not found");
			}

			int itemId = rs.id;

			if (ifVersion != null) {
				log.warn("CAS version swapping not implemented");
			}

			Tags tags = new Tags();
			mapToTags(db.listTagsForItem(itemId), tags);

			if (changeTags.addTags != null) {
				for (Tag addTag : changeTags.addTags) {
					if (tags.hasTag(addTag)) {
						continue;
					}
					db.insertTag(itemId, addTag);
					tags.add(addTag);
				}
			}

			if (changeTags.removeTags != null) {
				for (Tag removeTag : changeTags.removeTags) {
					boolean removed = tags.remove(removeTag);
					if (!removed) {
						continue;
					}
					db.removeTag(itemId, removeTag);
				}
			}

			return tags;
		} catch (SQLException e) {
			throw new RepositoryException("Error running query", e);
		} finally {
			db.close();
		}
	}

	private void mapToTags(List<TagEntity> tagEntities, Tags tags) {
		// Once REMOVE_DUPLICATE_TAGS is false, we can add direct to tags
		List<Tag> addList = Lists.newArrayList();

		for (TagEntity tag : tagEntities) {
			addList.add(Tag.build(tag.key, tag.data));
		}

		if (REMOVE_DUPLICATE_TAGS) {
			List<Tag> deduplicated = Lists.newArrayList();
			HashMultimap<String, String> valueMap = HashMultimap.create();
			for (Tag tag : addList) {
				if (valueMap.put(tag.getKey(), tag.getValue())) {
					deduplicated.add(tag);
				}
			}

			addList = deduplicated;
		}

		tags.addAll(addList);
	}

	byte[] serialize(ItemBase item, CryptoKey itemSecret) {

		// Remove fields that are stored in other columns

		// TODO: Is this the best way to do this?

		// We use JAXB to avoid requiring everything to implement Serializable
		ItemBase mutableItem = CloneHelpers.cloneViaJaxb(item);

		mutableItem.tags = null;
		mutableItem.key = null;
		mutableItem.version = 0;
		mutableItem.state = null;

		JaxbHelper jaxbHelper = JaxbHelper.get(item.getClass());

		StringWriter writer = new StringWriter();
		try {
			Marshaller marshaller = jaxbHelper.createMarshaller();

			// OpsSecretEncryptionStrategy strategy = new OpsSecretEncryptionStrategy(itemSecret);
			// strategy.setAdapter(marshaller);

			marshaller.marshal(mutableItem, writer);
		} catch (JAXBException e) {
			throw new IllegalArgumentException("Could not serialize data", e);
		}
		String xml = writer.toString();

		byte[] ciphertext = FathomdbCrypto.encrypt(itemSecret, Utf8.getBytes(xml));
		return ciphertext;
	}
}
