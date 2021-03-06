package org.platformlayer.ops.jobstore.jdbc;

import java.io.StringReader;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.platformlayer.PrimitiveComparators;
import org.platformlayer.RepositoryException;
import org.platformlayer.core.model.Action;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.ids.ManagedItemId;
import org.platformlayer.ids.ProjectId;
import org.platformlayer.jdbc.DbHelperBase;
import org.platformlayer.jobs.model.JobData;
import org.platformlayer.jobs.model.JobExecutionData;
import org.platformlayer.jobs.model.JobState;
import org.platformlayer.ops.tasks.JobQuery;
import org.platformlayer.xaas.repository.JobRepository;
import org.platformlayer.xaas.services.ServiceProviderDictionary;

import com.fathomdb.Casts;
import com.fathomdb.jdbc.JdbcConnection;
import com.fathomdb.jdbc.JdbcTransaction;
import com.fathomdb.jpa.Query;
import com.fathomdb.jpa.QueryFactory;
import com.fathomdb.jpa.QueryFilter;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/*
 create index job_execution_ix on job_execution (project, job_id, started_at);

 create view last_job_execution as select je.* from job_execution je join (select project, job_id, max(started_at) as max_started_at from job_execution group by project, job_id) as max_je on je.project=max_je.project and je.job_id=max_je.job_id and je.started_at = max_je.max_started_at;
 create view job_with_last_execution as select j.*, je.project as je_project, je.job_id as je_job_id, je.state as je_state, je.started_at as je_started_at, je.ended_at as je_ended_at, je.log_cookie as je_log_cookie from job j left outer join last_job_execution je on je.project = j.project and j.id = je.job_id;

 grant all on job_with_last_execution to user platformlayer_ops;

 alter table job add column lastrun_ended_at timestamp without time zone, add column lastrun_state character(1), add column lastrun_job_id text;


 */

public class JdbcJobRepository implements JobRepository {
	@Inject
	ServiceProviderDictionary serviceProviderDictionary;

	@Inject
	Provider<JdbcConnection> connectionProvider;

	static interface Queries {
		// @Query("INSERT INTO jobs (service, model, account, item, jobstate, data) VALUES (?, ?, ?, ?, ?, ?)")
		// int insertJobLog(int service, int model, int project, String key, int jobState, String data)
		// throws SQLException;

		@Query("SELECT * FROM job_execution WHERE project=? and job_id=?")
		List<JobExecutionEntity> listExecutions(int projectId, String jobId) throws SQLException;

		// @Query("SELECT * FROM job_execution")
		// List<JobExecutionEntity> listRecentExecutions(
		// @QueryFilter("project=?") int projectId,
		// @QueryFilter("(ended_at is null) or (ended_at >= (current_timestamp - (? * interval '1 second')))") Long
		// maxAgeSeconds)
		// throws SQLException;

		@Query("SELECT * FROM job")
		List<JobEntity> listRecentJobs(
				@QueryFilter("project=?") int projectId,
				// @QueryFilter("(lastrun_ended_at is null and ) or (lastrun_ended_at >= (current_timestamp - (? * interval '1 second')))")
				// Long maxAgeSeconds,
				@QueryFilter("(lastrun_ended_at >= (current_timestamp - (? * interval '1 second')))") Long maxAgeSeconds,
				@QueryFilter("target = ?") String target, @QueryFilter(QueryFilter.LIMIT) Integer limit);

		// @Query("SELECT * FROM job")
		// List<JobExecutionEntity> listRecentJobsAndExecutions(
		// @QueryFilter("project=?") int projectId,
		// @QueryFilter("(lastrun_ended_at is null) or (lastrun_ended_at >= (current_timestamp - (? * interval '1 second')))")
		// Long maxAgeSeconds,
		// @QueryFilter("target = ?") String target, @QueryFilter(QueryFilter.LIMIT) Integer limit);

		@Query("SELECT * FROM job WHERE project=? and id=?")
		JobEntity findJob(int projectId, String jobId) throws SQLException;

		@Query("SELECT * FROM job_execution WHERE project=? and job_id=? and id=?")
		JobExecutionEntity findExecution(int projectId, String jobId, String executionId) throws SQLException;

		@Query("UPDATE job_execution SET log_cookie=?, ended_at=?, state=? WHERE project=? and job_id=? and id=?")
		int updateExecution(String logCookie, Date endDate, JobState state, int projectId, String jobId,
				String executionId) throws SQLException;

		@Query("UPDATE job SET lastrun_ended_at=?, lastrun_state=?, lastrun_job_id=? WHERE project=? and id=?")
		int updateJob(Date endDate, JobState state, String executionId, int projectId, String jobId)
				throws SQLException;

		@Query(value = Query.AUTOMATIC_INSERT)
		int insert(JobExecutionEntity entity) throws SQLException;

		@Query(value = Query.AUTOMATIC_INSERT)
		int insert(JobEntity entity) throws SQLException;

	}

	@Inject
	QueryFactory queryFactory;

	@Inject
	JAXBContext jaxbContext;

	class DbHelper extends DbHelperBase {
		final Queries queries;

		public DbHelper() {
			super(connectionProvider.get());

			this.queries = queryFactory.get(Queries.class);
		}

		// public int insertJobLog(ServiceType serviceType, ItemType itemType, ProjectId project, ManagedItemId itemId,
		// JobState jobState, String data) throws SQLException {
		// return queries.insertJobLog(mapToValue(serviceType), mapToValue(itemType), mapToValue(project),
		// itemId.getKey(), jobState.getCode(), data);
		// }
	}

	// @Override
	// @JdbcTransaction
	// public void recordJob(PlatformLayerKey jobId, PlatformLayerKey itemKey, JobState jobState, JobLog jobLog)
	// throws RepositoryException {
	// DbHelper db = new DbHelper();
	// try {
	// String data;
	//
	// // TODO: More compact encoding?? XML InfoSet? GZIP?
	// try {
	// data = JaxbHelper.toXml(jobLog, false);
	// } catch (JAXBException e) {
	// throw new RepositoryException("Error serializing job log", e);
	// }
	//
	// int updateCount = db.insertJobLog(itemKey.getServiceType(), itemKey.getItemType(), itemKey.getProject(),
	// itemKey.getItemId(), jobState, data);
	//
	// if (updateCount != 1) {
	// throw new IllegalStateException("Unexpected number of rows inserted");
	// }
	// } catch (SQLException e) {
	// throw new RepositoryException("Error saving job log", e);
	// } finally {
	// db.close();
	// }
	// }

	@Override
	@JdbcTransaction
	public List<JobExecutionData> listExecutions(PlatformLayerKey jobKey) throws RepositoryException {
		DbHelper db = new DbHelper();
		try {
			ProjectId projectId = jobKey.getProject();
			String jobId = jobKey.getItemIdString();

			List<JobExecutionEntity> executions = db.queries.listExecutions(db.mapToValue(projectId), jobId);
			List<JobExecutionData> ret = Lists.newArrayList();
			for (JobExecutionEntity execution : executions) {
				ret.add(mapFromEntity(execution, jobKey));
			}

			sort(ret);

			return ret;
		} catch (SQLException e) {
			throw new RepositoryException("Error listing job executions", e);
		} finally {
			db.close();
		}
	}

	// @Override
	// @JdbcTransaction
	// public List<JobExecutionData> listRecentExecutions(JobQuery jobQuery) throws RepositoryException {
	// DbHelper db = new DbHelper();
	// try {
	// // We use JoinedQueryResult because we have a compound PK (projectId / jobId)
	// // and JPA makes this really complicated.
	//
	// ProjectId projectId = jobQuery.project;
	// Preconditions.checkNotNull(projectId);
	// int project = db.mapToValue(projectId);
	//
	// Long maxAge = null;
	// if (jobQuery.maxAge != null) {
	// maxAge = jobQuery.maxAge.getTotalSeconds();
	// }
	//
	// Integer limit = jobQuery.limit;
	// String filterTarget = jobQuery.target != null ? jobQuery.target.getUrl() : null;
	//
	// JoinedQueryResult results = db.queries.listRecentExecutions(project, maxAge, filterTarget, limit);
	//
	// List<JobExecutionData> ret = Lists.newArrayList();
	// Map<String, JobData> jobs = Maps.newHashMap();
	//
	// for (JobEntity job : results.getAll(JobEntity.class)) {
	// ManagedItemId jobId = new ManagedItemId(job.jobId);
	// PlatformLayerKey jobKey = JobData.buildKey(projectId, jobId);
	// jobs.put(job.jobId, mapFromEntity(job, jobKey));
	// }
	//
	// for (JobExecutionEntity execution : results.getAll(JobExecutionEntity.class)) {
	// JobData jobData = jobs.get(execution.jobId);
	// if (jobData == null) {
	// throw new IllegalStateException();
	// }
	//
	// ManagedItemId jobId = new ManagedItemId(execution.jobId);
	// PlatformLayerKey jobKey = JobData.buildKey(projectId, jobId);
	// JobExecutionData run = mapFromEntity(execution, jobKey);
	// run.job = jobData;
	// ret.add(run);
	// }
	//
	// sort(ret);
	//
	// return ret;
	// } catch (SQLException e) {
	// throw new RepositoryException("Error listing job executions", e);
	// } finally {
	// db.close();
	// }
	// }

	@Override
	@JdbcTransaction
	public List<JobData> listRecentJobs(JobQuery jobQuery) throws RepositoryException {
		DbHelper db = new DbHelper();
		try {
			ProjectId projectId = jobQuery.project;
			Preconditions.checkNotNull(projectId);
			int project = db.mapToValue(projectId);

			Long maxAge = null;
			if (jobQuery.maxAge != null) {
				maxAge = jobQuery.maxAge.getTotalSeconds();
			}

			Integer limit = jobQuery.limit;
			String filterTarget = jobQuery.target != null ? jobQuery.target.getUrl() : null;

			// TODO: Include currently running jobs...
			List<JobEntity> results = db.queries.listRecentJobs(project, maxAge, filterTarget, limit);
			List<JobData> ret = Lists.newArrayList();
			for (JobEntity job : results) {
				ManagedItemId jobId = new ManagedItemId(job.jobId);
				PlatformLayerKey jobKey = JobData.buildKey(projectId, jobId);
				JobData jobData = mapFromEntity(job, jobKey);
				ret.add(jobData);
			}

			sortJobs(ret);

			return ret;
		} catch (SQLException e) {
			throw new RepositoryException("Error listing job executions", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public JobExecutionData findExecution(PlatformLayerKey jobKey, String executionId) throws RepositoryException {
		DbHelper db = new DbHelper();
		try {
			ProjectId projectId = jobKey.getProject();
			String jobId = jobKey.getItemIdString();

			JobExecutionEntity execution = db.queries.findExecution(db.mapToValue(projectId), jobId, executionId);

			if (execution == null) {
				return null;
			}

			return mapFromEntity(execution, jobKey);
		} catch (SQLException e) {
			throw new RepositoryException("Error listing job executions", e);
		} finally {
			db.close();
		}
	}

	private JobExecutionData mapFromEntity(JobExecutionEntity execution, PlatformLayerKey jobKey) {
		JobExecutionData data = new JobExecutionData();
		data.executionId = execution.executionId;
		data.endedAt = execution.endedAt;
		data.startedAt = execution.startedAt;
		data.state = execution.state;
		data.executionId = execution.executionId;
		data.jobKey = jobKey;
		data.logCookie = execution.logCookie;
		return data;
	}

	private JobData mapFromEntity(JobEntity entity, PlatformLayerKey jobKey) throws RepositoryException {
		JobData data = new JobData();

		data.action = actionFromXml(entity.actionXml);
		data.key = jobKey;
		data.targetId = PlatformLayerKey.parse(entity.target);

		if (entity.lastrunId != null) {
			JobExecutionData execution = new JobExecutionData();
			execution.endedAt = entity.lastrunEndedAt;
			execution.executionId = entity.lastrunId;
			execution.state = entity.lastrunState;
			data.lastRun = execution;
		}

		return data;
	}

	private Action actionFromXml(String actionXml) throws RepositoryException {
		Object o;
		try {
			Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
			o = unmarshaller.unmarshal(new StringReader(actionXml));
		} catch (JAXBException e) {
			throw new RepositoryException("Error deserializing action", e);
		}

		return Casts.checkedCast(o, Action.class);
	}

	private String toXml(Action action) throws RepositoryException {
		Object o;
		try {
			Marshaller marshaller = jaxbContext.createMarshaller();
			StringWriter writer = new StringWriter();
			marshaller.marshal(action, writer);
			return writer.toString();
		} catch (JAXBException e) {
			throw new RepositoryException("Error serializing action", e);
		}
	}

	@Override
	@JdbcTransaction
	public JobData findJob(PlatformLayerKey jobKey) throws RepositoryException {
		DbHelper db = new DbHelper();
		try {
			ProjectId projectId = jobKey.getProject();
			String jobId = jobKey.getItemIdString();

			JobEntity execution = db.queries.findJob(db.mapToValue(projectId), jobId);

			if (execution == null) {
				return null;
			}

			return mapFromEntity(execution, jobKey);
		} catch (SQLException e) {
			throw new RepositoryException("Error listing job executions", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public void recordJobEnd(PlatformLayerKey jobKey, String executionId, Date endedAt, JobState state, String logCookie)
			throws RepositoryException {

		DbHelper db = new DbHelper();
		try {
			ProjectId project = jobKey.getProject();
			String jobId = jobKey.getItemIdString();

			int projectId = db.mapToValue(project);
			int updateCount = db.queries.updateExecution(logCookie, endedAt, state, projectId, jobId, executionId);
			if (updateCount != 1) {
				throw new RepositoryException("Unexpected number of rows updated");
			}

			updateCount = db.queries.updateJob(endedAt, state, executionId, projectId, jobId);
			if (updateCount != 1) {
				throw new RepositoryException("Unexpected number of rows updated");
			}
		} catch (SQLException e) {
			throw new RepositoryException("Error updating job execution", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public String insertExecution(PlatformLayerKey jobKey, Date startedAt) throws RepositoryException {
		DbHelper db = new DbHelper();
		try {
			ProjectId projectId = jobKey.getProject();
			String jobId = jobKey.getItemIdString();

			String executionId = UUID.randomUUID().toString();

			JobExecutionEntity execution = new JobExecutionEntity();
			execution.project = db.mapToValue(projectId);
			execution.startedAt = startedAt;
			execution.state = JobState.RUNNING;
			execution.executionId = executionId;
			execution.jobId = jobId;

			int updateCount = db.queries.insert(execution);
			if (updateCount != 1) {
				throw new RepositoryException("Unexpected number of rows inserted");
			}

			return execution.executionId;
		} catch (SQLException e) {
			throw new RepositoryException("Error inserting job execution", e);
		} finally {
			db.close();
		}
	}

	@Override
	@JdbcTransaction
	public String insertJob(ProjectId projectId, JobData jobData) throws RepositoryException {
		DbHelper db = new DbHelper();
		try {
			String jobId = UUID.randomUUID().toString();

			JobEntity entity = new JobEntity();
			entity.project = db.mapToValue(projectId);
			entity.jobId = jobId;
			entity.actionXml = toXml(jobData.action);
			entity.target = jobData.targetId.getUrl();

			int updateCount = db.queries.insert(entity);
			if (updateCount != 1) {
				throw new RepositoryException("Unexpected number of rows inserted");
			}

			return jobId;
		} catch (SQLException e) {
			throw new RepositoryException("Error inserting job execution", e);
		} finally {
			db.close();
		}
	}

	protected void sort(List<JobExecutionData> runs) {
		if (runs == null) {
			return;
		}

		Collections.sort(runs, new Comparator<JobExecutionData>() {
			@Override
			public int compare(JobExecutionData o1, JobExecutionData o2) {
				Date v1 = o1.startedAt;
				Date v2 = o2.startedAt;

				return PrimitiveComparators.compare(v1, v2);
			}
		});
	}

	protected void sortJobs(List<JobData> runs) {
		if (runs == null) {
			return;
		}

		Collections.sort(runs, new Comparator<JobData>() {
			Date getLastRun(JobData d) {
				JobExecutionData lastRun = d.getLastRun();
				if (lastRun == null) {
					return null;
				}
				return lastRun.getEndedAt();
			}

			@Override
			public int compare(JobData o1, JobData o2) {
				Date v1 = getLastRun(o1);
				Date v2 = getLastRun(o2);

				return PrimitiveComparators.compare(v1, v2);
			}
		});
	}
}
