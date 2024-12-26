/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sk.drndos.keycloak.crowd.storage;

import com.atlassian.crowd.embedded.api.SearchRestriction;
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.search.builder.Combine;
import com.atlassian.crowd.search.builder.Restriction;
import com.atlassian.crowd.search.query.entity.restriction.constants.UserTermKeys;
import com.atlassian.crowd.service.client.CrowdClient;
import lombok.extern.jbosslog.JBossLog;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.*;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@JBossLog
public class CrowdUserStorageProvider implements
        UserStorageProvider,
        UserLookupProvider,
        UserQueryProvider,
        CredentialInputValidator {

    private final KeycloakSession session;
    private final ComponentModel model;
    private final CrowdClient client;


    public CrowdUserStorageProvider(KeycloakSession session, ComponentModel model, CrowdClient client) {
        this.session = session;
        this.model = model;
        this.client = client;
    }

    // UserLookupProvider methods

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        log.info("Getting user by username " + username);
        try {
            User user = client.getUser(username);
            return new UserAdapter(session, realm, model, user);
        } catch (Exception e) {
            log.info(e.getLocalizedMessage(), e);
            return null;
        }
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        log.info("Getting user by id " + id);
        StorageId storageId = new StorageId(id);
        String username = storageId.getExternalId();
        return getUserByUsername(realm, username);
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        throw new UnsupportedOperationException("Not supported by Crowd");
    }

    // UserQueryProvider methods

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params,
                                                 Integer firstResult, Integer maxResults) {

        String searchWord = params.getOrDefault(UserModel.SEARCH, "");
        searchWord = "*".equalsIgnoreCase(searchWord) ? "" : searchWord;
        SearchRestriction byName = Restriction.on(UserTermKeys.USERNAME).containing(searchWord);
        SearchRestriction isActive = Restriction.on(UserTermKeys.ACTIVE).exactlyMatching(true);
        SearchRestriction restriction = Combine.allOf(isActive, byName);

        try {
            List<User> rawUsers = client.searchUsers(restriction, firstResult, maxResults);
            return rawUsers.stream().map(user -> new UserAdapter(session, realm, model, user));
        } catch (Exception e) {
            log.error(e.getLocalizedMessage(), e);
            return null;
        }
    }

    /**
     * For pagination.
     */
    @Override
    public int getUsersCount(RealmModel realm, Map<String, String> params) {
        return (int) this.searchForUserStream(realm, params, 0, Integer.MAX_VALUE).count();
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group,
                                                   Integer firstResult, Integer maxResults) {
        // runtime automatically handles querying UserFederatedStorage
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, String attrName, String attrValue) {
        // runtime automatically handles querying UserFederatedStorage
        return Stream.empty();
    }

    // CredentialInputValidator methods

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        log.info("Is user " + user.getUsername() + " configured for ? " + credentialType);
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        log.info("Does realm support ? " + credentialType);
        return credentialType.equals(PasswordCredentialModel.TYPE);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput input) {
        if (!supportsCredentialType(input.getType()) || !(input instanceof UserCredentialModel cred)) {
            return false;
        }

        log.info("Is user valid ? " + user.getUsername());
        try {
            User authenticatedUser = client.authenticateUser(user.getUsername(), cred.getValue());
            return authenticatedUser != null;
        } catch (Exception e) {
            log.info(e.getLocalizedMessage(), e);
            return false;
        }
    }

    @Override
    public void close() {

    }
}
