package org.platformlayer.ops.schedule.jdbc;

import java.sql.SQLException;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.xml.bind.JAXBException;

import org.platformlayer.RepositoryException;
import org.platformlayer.core.model.Action;
import org.platformlayer.core.model.JobSchedule;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.core.model.Secret;
import org.platformlayer.jdbc.DbHelperBase;
import org.platformlayer.ops.OpsException;
import org.platformlayer.ops.schedule.ActionTask;
import org.platformlayer.ops.schedule.EndpointRecord;
import org.platformlayer.ops.schedule.JobExecution;
import org.platformlayer.ops.schedule.SchedulerRecord;
import org.platformlayer.ops.schedule.SchedulerRepository;
import org.platformlayer.xml.JaxbHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fathomdb.jdbc.JdbcConnection;
import com.fathomdb.jdbc.JdbcTransaction;
import com.fathomdb.jpa.Query;
import com.fathomdb.jpa.QueryFactory;
import com.fathomdb.utils.Hex;
import com.google.common.collect.Lists;

public class JdbcSchedulerRepository implements SchedulerRepository {
	private static final Logger log = LoggerFactory.getLogger(JdbcSchedulerRepository.class);

	@Inject
	Provider<JdbcConnection> connectionProvider;

	static interface Queries {
		@Query("SELECT * FROM scheduler_job WHERE key=?")
		SchedulerRecordEntity findByKey(String key) throws SQLException;

		@Query("SELECT * FROM scheduler_job")
		List<SchedulerRecordEntity> listItems() throws SQLException;

		@Query(Query.AUTOMATIC_INSERT)
		int insertItem(SchedulerRecordEntity entity) throws SQLException;

		@Query(Query.AUTOMATIC_UPDATE)
		int updateItem(SchedulerRecordEntity entity) throws SQLException;
	}

	@Inject
	QueryFactory queryFactory;

	class DbHelper extends DbHelperBase {
		final Queries queries;

		public DbHelper() {
			super(connectionProvider.get());

			this.queries = queryFactory.get(Queries.class);
		}

		public SchedulerRecordEntity findByKey(String key) throws SQLException {
			return queries.findByKey(key);
		}

		public List<SchedulerRecordEntity> listItems() throws SQLException {
			return queries.listItems();
		}

		public void insertItem(SchedulerRecordEntity entity) throws SQLException {
			int updateCount = queries.insertItem(entity);
			if (updateCount != 1) {
				throw new IllegalStateException("Unexpected number of rows inserted");
			}
		}

		public void updateItem(SchedulerRecordEntity entity) throws SQLException {
			int updateCount = queries.updateItem(entity);
			if (updateCount != 1) {
				throw new IllegalStateException("Unexpected number of rows updated");
			}
		}
	}

	private SchedulerRecord fromDb(SchedulerRecordEntity in) throws OpsException {
		SchedulerRecord out = new SchedulerRecord();
		out.key = in.key;

		out.schedule = new JobSchedule();
		out.schedule.base = in.scheduleBase;
		out.schedule.interval = in.scheduleInterval;

		ActionTask task = new ActionTask();
		out.task = task;

		task.target = PlatformLayerKey.parse(in.taskTarget);
		task.endpoint = new EndpointRecord();
		task.endpoint.url = in.taskEndpointUrl;
		task.endpoint.project = in.taskEndpointProject;
		task.endpoint.secret = fromDb(in.taskEndpointSecret);
		task.endpoint.token = in.taskEndpointToken;
		task.endpoint.trustKeys = in.taskEndpointTrustKeys;

		if (in.taskAction != null) {
			task.action = deserializeAction(in.taskAction);
		}

		return out;
	}

	private Action deserializeAction(String xml) throws OpsException {
		try {
			JaxbHelper jaxbHelper = JaxbHelper.get(Action.class);
			return (Action) jaxbHelper.unmarshal(xml);
		} catch (JAXBException e) {
			throw new OpsException("Error deserializing action", e);
		}
	}

	private String serializeAction(Action action) throws OpsException {
		try {
			return JaxbHelper.toXml(action, false);
		} catch (JAXBException e) {
			throw new OpsException("Error serializing action", e);
		}
	}

	private SchedulerRecordEntity toDb(SchedulerRecord in) throws OpsException {
		SchedulerRecordEntity out = new SchedulerRecordEntity();
		out.key = in.key;

		if (in.schedule != null) {
			out.scheduleBase = in.schedule.base;
			out.scheduleInterval = in.schedule.interval;
		}

		if (in.task instanceof ActionTask) {
			ActionTask task = (ActionTask) in.task;

			out.taskTarget = task.target.getUrl();

			if (task.endpoint != null) {
				out.taskEndpointUrl = task.endpoint.url;
				out.taskEndpointProject = task.endpoint.project;
				out.taskEndpointSecret = toDb(task.endpoint.secret);
				out.taskEndpointToken = task.endpoint.token;
				out.taskEndpointTrustKeys = task.endpoint.trustKeys;
			}

			if (task.action != null) {
				out.taskAction = serializeAction(task.action);
			}
		} else {
			throw new UnsupportedOperationException();
		}

		return out;
	}

	private Secret fromDb(byte[] in) {
		// TODO: Implement encryption!
		log.warn("Encryption not implemented for scheduler");

		if (in == null) {
			return null;
		}
		return Secret.build(Hex.toHex(in));
	}

	private byte[] toDb(Secret secret) {
		// TODO: Implement encryption!
		log.warn("Encryption not implemented for scheduler");

		if (secret == null) {
			return null;
		}

		String plaintext = secret.plaintext();
		return Hex.fromHex(plaintext);
	}

	@Override
	@JdbcTransaction
	public SchedulerRecord find(String key) throws RepositoryException {
		DbHelper db = new DbHelper();

		try {
			SchedulerRecordEntity entity = db.findByKey(key);
			if (entity == null) {
				return null;
			}
			return fromDb(entity);

		} catch (SQLException e) {
			throw new RepositoryException("Error running query", e);
		} catch (OpsException e) {
			throw new RepositoryException("Error deserializing from database", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public void put(SchedulerRecord record) throws RepositoryException {
		DbHelper db = new DbHelper();

		try {
			String key = record.key;

			SchedulerRecordEntity entity = toDb(record);
			SchedulerRecordEntity existing = db.findByKey(key);
			if (existing == null) {
				db.insertItem(entity);
			} else {
				db.updateItem(entity);
			}
		} catch (SQLException e) {
			throw new RepositoryException("Error running query", e);
		} catch (OpsException e) {
			throw new RepositoryException("Error serializing to database", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public Iterable<SchedulerRecord> findAll() throws RepositoryException {
		DbHelper db = new DbHelper();

		try {
			List<SchedulerRecord> ret = Lists.newArrayList();
			for (SchedulerRecordEntity entity : db.listItems()) {
				ret.add(fromDb(entity));
			}
			return ret;
		} catch (SQLException e) {
			throw new RepositoryException("Error running query", e);
		} catch (OpsException e) {
			throw new RepositoryException("Error deserializing from database", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public void logExecution(String key, JobExecution execution, Throwable jobException) throws RepositoryException {
		// TODO: Implement properly??
		log.info("TASK EXECUTED: " + key + " success=" + execution.success);
	}
}
