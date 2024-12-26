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

import com.atlassian.crowd.integration.rest.entity.UserEntity;
import com.atlassian.crowd.model.user.User;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 *
 * motivated by https://github.com/keycloak/keycloak-quickstarts/blob/main/extension/user-storage-jpa/src/main/java/org/keycloak/quickstart/storage/user/UserAdapter.java
 */
public class UserAdapter extends AbstractUserAdapterFederatedStorage {

    private final User user;
    private final String keycloakId;

    private final Map<String, Function<User, String>> attributeFunctions = new HashMap<>() {{
        put("displayName", User::getDisplayName);
    }};

    public UserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model, User user) {
        super(session, realm, model);
        this.user = user;
        this.keycloakId = StorageId.keycloakId(model, user.getName());
    }

    @Override
    public String getFirstName() {
        return user.getFirstName();
    }

    @Override
    public String getLastName() {
        return user.getLastName();
    }

    @Override
    public String getUsername() {
        return user.getName();
    }

    @Override
    public void setUsername(String username) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getEmail() {
        return user.getEmailAddress();
    }

    @Override
    public void setEmail(String email) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * hack to show createdDate on user page.
     */
    @Override
    public Long getCreatedTimestamp() {
        if (user instanceof UserEntity userEntity) {
            if (userEntity.getCreatedDate() != null) {
                return userEntity.getCreatedDate().getTime();
            }
        }
        return super.getCreatedTimestamp();
    }

    @Override
    public String getId() {
        return keycloakId;
    }

    @Override
    public void setSingleAttribute(String name, String value) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void removeAttribute(String name) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void setAttribute(String name, List<String> values) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public String getFirstAttribute(String name) {
        return attributeFunctions.getOrDefault(name, key -> super.getFirstAttribute(name)).apply(user);
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> attrs = super.getAttributes();
        MultivaluedHashMap<String, String> all = new MultivaluedHashMap<>();
        all.putAll(attrs);
        attributeFunctions.forEach((key, value) -> all.add(key, value.apply(user)));
        return all;
    }

    @Override
    public Stream<String> getAttributeStream(String name) {
        if (attributeFunctions.containsKey(name)) {
            return Stream.of(attributeFunctions.get(name).apply(user));
        } else {
            return super.getAttributeStream(name);
        }
    }
}
