/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.repository.redis.management;

import io.gravitee.repository.exceptions.TechnicalException;
import io.gravitee.repository.management.api.GroupRepository;
import io.gravitee.repository.management.model.Group;
import io.gravitee.repository.redis.management.internal.GroupRedisRepository;
import io.gravitee.repository.redis.management.model.RedisGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Nicolas GERAUD (nicolas.geraud at graviteesource.com) 
 * @author GraviteeSource Team
 */
@Component
public class RedisGroupRepository implements GroupRepository{

    @Autowired
    private GroupRedisRepository internalRepository;

    @Override
    public Optional<Group> findById(String groupId) throws TechnicalException {
        return Optional.ofNullable(convert(internalRepository.find(groupId)));
    }

    @Override
    public Group create(Group item) throws TechnicalException {
        if (item != null && item.getAdministrators() == null) {
            item.setAdministrators(Collections.emptyList());
        }
        return convert(internalRepository.saveOrUpdate(convert(item)));
    }

    @Override
    public Group update(Group item) throws TechnicalException {
        return convert(internalRepository.saveOrUpdate(convert(item)));
    }

    @Override
    public void delete(String groupId) throws TechnicalException {
        internalRepository.delete(groupId);
    }

    @Override
    public Set<Group> findByType(Group.Type type) throws TechnicalException {
        return internalRepository.findByType(type.name())
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<Group> findAll() throws TechnicalException {
        return internalRepository.findAll()
                .stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    private Group convert(RedisGroup redisGroup) {
        if (redisGroup==null) {
            return null;
        }
        Group group = new Group();
        group.setId(redisGroup.getId());
        group.setName(redisGroup.getName());
        group.setType(Group.Type.valueOf(redisGroup.getType()));
        group.setCreatedAt(redisGroup.getCreatedAt());
        group.setUpdatedAt(redisGroup.getUpdatedAt());
        group.setAdministrators(redisGroup.getAdminstrators());
        return group;
    }

    private RedisGroup convert(Group group) {
        if (group==null) {
            return null;
        }
        RedisGroup redisGroup = new RedisGroup();
        redisGroup.setId(group.getId());
        redisGroup.setName(group.getName());
        redisGroup.setType(group.getType().name());
        redisGroup.setCreatedAt(group.getCreatedAt());
        redisGroup.setUpdatedAt(group.getUpdatedAt());
        redisGroup.setAdminstrators(group.getAdministrators());
        return redisGroup;
    }
}
