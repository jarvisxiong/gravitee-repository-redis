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
import io.gravitee.repository.management.api.ApplicationRepository;
import io.gravitee.repository.management.model.Application;
import io.gravitee.repository.management.model.Membership;
import io.gravitee.repository.management.model.MembershipType;
import io.gravitee.repository.redis.management.internal.ApplicationRedisRepository;
import io.gravitee.repository.redis.management.internal.MembershipRedisRepository;
import io.gravitee.repository.redis.management.model.RedisApplication;
import io.gravitee.repository.redis.management.model.RedisMembership;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 * @author GraviteeSource Team
 */
@Component
public class RedisApplicationRepository implements ApplicationRepository {

    @Autowired
    private ApplicationRedisRepository applicationRedisRepository;

    @Autowired
    private MembershipRedisRepository membershipRedisRepository;

    @Override
    public Set<Application> findAll() throws TechnicalException {
        Set<RedisApplication> applications = applicationRedisRepository.findAll();

        return applications.stream()
                .map(this::convert)
                .collect(Collectors.toSet());
    }

    @Override
    public Optional<Application> findById(String applicationId) throws TechnicalException {
        RedisApplication redisApplication = this.applicationRedisRepository.find(applicationId);
        return Optional.ofNullable(convert(redisApplication));
    }

    @Override
    public Application create(Application application) throws TechnicalException {
        RedisApplication redisApplication = applicationRedisRepository.saveOrUpdate(convert(application));
        return convert(redisApplication);
    }

    @Override
    public Application update(Application application) throws TechnicalException {
        RedisApplication redisApplication = applicationRedisRepository.saveOrUpdate(convert(application));
        return convert(redisApplication);
    }

    @Override
    public void delete(String applicationId) throws TechnicalException {
        applicationRedisRepository.delete(applicationId);
    }

    @Override
    public Set<Application> findByUser(String username, MembershipType membershipType) throws TechnicalException {
        Set<RedisMembership> memberships = membershipRedisRepository.getMemberships(username);

        return memberships.stream()
                .filter(redisMembership ->
                        redisMembership.getMembershipFor() == RedisMembership.MembershipFor.APPLICATION &&
                                (membershipType == null || (membershipType.name().equals(redisMembership.getMembershipType()
                ))))
                .map(RedisMembership::getOwner)
                .distinct()
                .map(application -> convert(applicationRedisRepository.find(application)))
                .collect(Collectors.toSet());
    }

    @Override
    public void saveMember(String applicationId, String username, MembershipType membershipType) throws TechnicalException {
        // Add member into the application
        RedisApplication redisApplication = applicationRedisRepository.find(applicationId);
        redisApplication.getMembers().add(username);
        applicationRedisRepository.saveOrUpdate(redisApplication);

        // Save or saveOrUpdate membership entity
        Set<RedisMembership> memberships = membershipRedisRepository.getMemberships(username);
        List<RedisMembership> membershipList = new ArrayList<>(memberships);

        RedisMembership membership = new RedisMembership();
        membership.setOwner(applicationId);
        membership.setMembershipFor(RedisMembership.MembershipFor.APPLICATION);

        int idx = membershipList.indexOf(membership);
        if (idx != -1) {
            membership = membershipList.get(idx);
            membership.setMembershipType(membershipType.name());
            membership.setUpdatedAt(new Date().getTime());
        } else {
            membership.setMembershipType(membershipType.name());
            membership.setCreatedAt(new Date().getTime());
            membership.setUpdatedAt(membership.getCreatedAt());
            memberships.add(membership);
        }
        membershipRedisRepository.save(username, memberships);
    }

    @Override
    public void deleteMember(String applicationId, String username) throws TechnicalException {
        // Remove member from the application
        RedisApplication redisApplication = applicationRedisRepository.find(applicationId);
        redisApplication.getMembers().remove(username);
        applicationRedisRepository.saveOrUpdate(redisApplication);

        Set<RedisMembership> memberships = membershipRedisRepository.getMemberships(username);

        RedisMembership membership = new RedisMembership();
        membership.setOwner(applicationId);
        membership.setMembershipFor(RedisMembership.MembershipFor.APPLICATION);

        if (memberships.contains(membership)) {
            memberships.remove(membership);
            membershipRedisRepository.save(username, memberships);
        }
    }

    @Override
    public Collection<Membership> getMembers(String applicationId, MembershipType membershipType) throws TechnicalException {
        RedisApplication application = applicationRedisRepository.find(applicationId);
        Set<Membership> memberships = new HashSet<>();

        for(String member : application.getMembers()) {
            Set<RedisMembership> redisMemberships = membershipRedisRepository.getMemberships(member);

            redisMemberships.stream()
                    .filter(redisMembership ->
                            (redisMembership.getMembershipFor() == RedisMembership.MembershipFor.APPLICATION) &&
                            redisMembership.getOwner().equals(applicationId))
                    .filter(membership -> membershipType.name().equalsIgnoreCase(membership.getMembershipType()))
                    .forEach(redisMembership -> memberships.add(convert(redisMembership)));
        }

        return memberships;
    }

    @Override
    public Membership getMember(String applicationId, String username) throws TechnicalException {
        Set<RedisMembership> memberships = membershipRedisRepository.getMemberships(username);

        for(RedisMembership redisMembership : memberships) {
            if (redisMembership.getMembershipFor() == RedisMembership.MembershipFor.APPLICATION &&
                    redisMembership.getOwner().equals(applicationId)) {
                return convert(redisMembership);
            }
        }

        return null;
    }

    private Application convert(RedisApplication redisApplication) {
        if (redisApplication == null) {
            return null;
        }

        Application application = new Application();

        application.setId(redisApplication.getId());
        application.setName(redisApplication.getName());
        application.setCreatedAt(new Date(redisApplication.getCreatedAt()));
        application.setUpdatedAt(new Date(redisApplication.getUpdatedAt()));
        application.setDescription(redisApplication.getDescription());
        application.setType(redisApplication.getType());
        return application;
    }

    private RedisApplication convert(Application application) {
        RedisApplication redisApplication = new RedisApplication();

        redisApplication.setId(application.getId());
        redisApplication.setName(application.getName());
        redisApplication.setCreatedAt(application.getCreatedAt().getTime());
        redisApplication.setUpdatedAt(application.getUpdatedAt().getTime());
        redisApplication.setDescription(application.getDescription());
        redisApplication.setType(application.getType());

        return redisApplication;
    }

    private Membership convert(RedisMembership redisMembership) {
        Membership membership = new Membership();

        //TODO: map user
        membership.setUser(null);
        membership.setCreatedAt(new Date(redisMembership.getCreatedAt()));
        membership.setUpdatedAt(new Date(redisMembership.getUpdatedAt()));
        membership.setMembershipType(MembershipType.valueOf(redisMembership.getMembershipType()));

        return membership;
    }
}