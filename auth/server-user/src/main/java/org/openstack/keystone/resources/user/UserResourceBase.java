package org.openstack.keystone.resources.user;

import java.util.Date;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.platformlayer.RepositoryException;
import org.platformlayer.auth.ProjectEntity;
import org.platformlayer.auth.UserEntity;
import org.platformlayer.auth.model.Access;
import org.platformlayer.auth.model.Token;
import org.platformlayer.auth.resources.PlatformlayerAuthResourceBase;
import org.platformlayer.auth.services.TokenInfo;
import org.platformlayer.auth.services.TokenService;

import com.google.common.collect.Lists;

public class UserResourceBase extends PlatformlayerAuthResourceBase {
	static final Logger log = Logger.getLogger(UserResourceBase.class);

	@Inject
	TokenService tokenService;

	protected Access buildAccess(UserEntity user) {
		TokenInfo token = buildToken("" + user.getId(), user.getTokenSecret());

		Access access = new Access();
		// response.access.serviceCatalog = serviceMapper.getServices(userInfo, project);
		access.token = new Token();
		access.token.expires = token.expiration;
		access.token.id = tokenService.encodeToken(token);

		access.projects = Lists.newArrayList();
		try {
			for (ProjectEntity project : userAuthenticator.listProjects(user)) {
				access.projects.add(project.getName());
			}
		} catch (RepositoryException e) {
			log.warn("Error while listing projects for user: " + user.key, e);
			throwInternalError();
		}

		return access;
	}

	private TokenInfo buildToken(String userId, byte[] tokenSecret) {
		Date now = new Date();
		Date expiration = TOKEN_VALIDITY.addTo(now);

		byte flags = 0;
		TokenInfo tokenInfo = new TokenInfo(flags, userId, expiration, tokenSecret);

		return tokenInfo;
	}
}
