package org.platformlayer.auth.keystone;

import org.platformlayer.auth.JdbcUserRepository;
import org.platformlayer.auth.KeystoneJdbcModule;
import org.platformlayer.auth.ProjectEntity;
import org.platformlayer.auth.ServiceAccountEntity;
import org.platformlayer.auth.UserDatabase;
import org.platformlayer.auth.UserEntity;
import org.platformlayer.jdbc.simplejpa.ResultSetMappers;
import org.platformlayer.jdbc.simplejpa.ResultSetMappersProvider;

import com.google.inject.AbstractModule;

public class KeystoneOpsUserModule extends AbstractModule {

	@Override
	protected void configure() {
		install(new KeystoneJdbcModule());

		bind(UserDatabase.class).to(JdbcUserRepository.class).asEagerSingleton();
		bind(ResultSetMappers.class).toProvider(
				ResultSetMappersProvider.build(UserEntity.class, ProjectEntity.class, ServiceAccountEntity.class));
	}
}