/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements. See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.openmetadata.catalog.util;

import org.openmetadata.catalog.Entity;
import org.openmetadata.catalog.entity.data.Chart;
import org.openmetadata.catalog.entity.data.Dashboard;
import org.openmetadata.catalog.entity.data.Database;
import org.openmetadata.catalog.entity.data.Metrics;
import org.openmetadata.catalog.entity.data.Model;
import org.openmetadata.catalog.entity.data.Pipeline;
import org.openmetadata.catalog.entity.data.Report;
import org.openmetadata.catalog.entity.data.Table;
import org.openmetadata.catalog.entity.data.Task;
import org.openmetadata.catalog.entity.data.Topic;
import org.openmetadata.catalog.entity.services.DashboardService;
import org.openmetadata.catalog.entity.services.DatabaseService;
import org.openmetadata.catalog.entity.services.MessagingService;
import org.openmetadata.catalog.entity.services.PipelineService;
import org.openmetadata.catalog.entity.teams.Team;
import org.openmetadata.catalog.entity.teams.User;
import org.openmetadata.catalog.exception.CatalogExceptionMessage;
import org.openmetadata.catalog.exception.EntityNotFoundException;
import org.openmetadata.catalog.jdbi3.ChartDAO;
import org.openmetadata.catalog.jdbi3.ChartDAO3;
import org.openmetadata.catalog.jdbi3.DashboardDAO;
import org.openmetadata.catalog.jdbi3.DashboardDAO3;
import org.openmetadata.catalog.jdbi3.DatabaseDAO;
import org.openmetadata.catalog.jdbi3.DatabaseDAO3;
import org.openmetadata.catalog.jdbi3.EntityRelationshipDAO3;
import org.openmetadata.catalog.jdbi3.EntityRepository;
import org.openmetadata.catalog.jdbi3.EntityRelationshipDAO;
import org.openmetadata.catalog.jdbi3.MetricsDAO;
import org.openmetadata.catalog.jdbi3.MetricsDAO3;
import org.openmetadata.catalog.jdbi3.ModelDAO;
import org.openmetadata.catalog.jdbi3.ModelDAO3;
import org.openmetadata.catalog.jdbi3.PipelineDAO;
import org.openmetadata.catalog.jdbi3.PipelineDAO3;
import org.openmetadata.catalog.jdbi3.Relationship;
import org.openmetadata.catalog.jdbi3.ReportDAO;
import org.openmetadata.catalog.jdbi3.ReportDAO3;
import org.openmetadata.catalog.jdbi3.TableDAO;
import org.openmetadata.catalog.jdbi3.TableDAO3;
import org.openmetadata.catalog.jdbi3.TagDAO3;
import org.openmetadata.catalog.jdbi3.TagDAO;
import org.openmetadata.catalog.jdbi3.TaskDAO;
import org.openmetadata.catalog.jdbi3.TaskDAO3;
import org.openmetadata.catalog.jdbi3.TeamDAO3;
import org.openmetadata.catalog.jdbi3.TeamDAO;
import org.openmetadata.catalog.jdbi3.TopicDAO;
import org.openmetadata.catalog.jdbi3.TopicDAO3;
import org.openmetadata.catalog.jdbi3.UsageDAO;
import org.openmetadata.catalog.jdbi3.UsageDAO3;
import org.openmetadata.catalog.jdbi3.UserDAO;
import org.openmetadata.catalog.jdbi3.UserDAO3;
import org.openmetadata.catalog.resources.charts.ChartResource;
import org.openmetadata.catalog.resources.dashboards.DashboardResource;
import org.openmetadata.catalog.resources.databases.DatabaseResource;
import org.openmetadata.catalog.resources.databases.TableResource;
import org.openmetadata.catalog.resources.feeds.MessageParser.EntityLink;
import org.openmetadata.catalog.resources.models.ModelResource;
import org.openmetadata.catalog.resources.pipelines.PipelineResource;
import org.openmetadata.catalog.resources.services.dashboard.DashboardServiceResource;
import org.openmetadata.catalog.resources.services.database.DatabaseServiceResource;
import org.openmetadata.catalog.resources.services.messaging.MessagingServiceResource;
import org.openmetadata.catalog.resources.services.pipeline.PipelineServiceResource;
import org.openmetadata.catalog.resources.tasks.TaskResource;
import org.openmetadata.catalog.resources.teams.TeamResource;
import org.openmetadata.catalog.resources.teams.UserResource;
import org.openmetadata.catalog.resources.topics.TopicResource;
import org.openmetadata.catalog.type.EntityReference;
import org.openmetadata.catalog.type.Tag;
import org.openmetadata.catalog.type.TagLabel;
import org.openmetadata.catalog.type.TagLabel.LabelType;
import org.openmetadata.catalog.type.UsageDetails;
import org.openmetadata.catalog.type.UsageStats;
import org.openmetadata.common.utils.CipherText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EntityUtil {
  private static final Logger LOG = LoggerFactory.getLogger(EntityUtil.class);

  private EntityUtil() {

  }

  /**
   * Validate that JSON payload can be turned into POJO object
   */
  public static <T> T validate(String identity, String json, Class<T> clz) throws WebApplicationException, IOException {
    T entity = null;
    if (json != null) {
      entity = JsonUtils.readValue(json, clz);
    }
    if (entity == null) {
      throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(clz.getSimpleName(), identity));
    }
    return entity;
  }

  public static EntityReference getService(EntityRelationshipDAO dao, UUID entityId) {
    List<EntityReference> refs = dao.findFrom(entityId.toString(), Relationship.CONTAINS.ordinal());
    if (refs.size() > 1) {
      LOG.warn("Possible database issues - multiple services found for entity {}", entityId);
      return refs.get(0);
    }
    return refs.isEmpty() ? null : refs.get(0);
  }

  public static EntityReference getService(EntityRelationshipDAO3 dao, UUID entityId) {
    List<EntityReference> refs = dao.findFrom(entityId.toString(), Relationship.CONTAINS.ordinal());
    if (refs.size() > 1) {
      LOG.warn("Possible database issues - multiple services found for entity {}", entityId);
      return refs.get(0);
    }
    return refs.isEmpty() ? null : refs.get(0);
  }

  public static EntityReference getService(EntityRelationshipDAO dao, UUID entityId, String serviceType) {
    List<EntityReference> refs = dao.findFromEntity(entityId.toString(), Relationship.CONTAINS.ordinal(), serviceType);
    if (refs.size() > 1) {
      LOG.warn("Possible database issues - multiple services found for entity {}", entityId);
      return refs.get(0);
    }
    return refs.isEmpty() ? null : refs.get(0);
  }

  public static EntityReference getService(EntityRelationshipDAO3 dao, UUID entityId, String serviceType) {
    List<EntityReference> refs = dao.findFromEntity(entityId.toString(), Relationship.CONTAINS.ordinal(), serviceType);
    if (refs.size() > 1) {
      LOG.warn("Possible database issues - multiple services found for entity {}", entityId);
      return refs.get(0);
    }
    return refs.isEmpty() ? null : refs.get(0);
  }

  /**
   * Populate EntityRef with href
   */
  public static void addHref(UriInfo uriInfo, EntityReference ref) {
    if (ref == null) {
      return;
    }
    String entity = ref.getType();
    if (entity.equalsIgnoreCase(Entity.TEAM)) {
      TeamResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.USER)) {
      UserResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.TABLE)) {
      TableResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.DATABASE)) {
      DatabaseResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.TOPIC)) {
      TopicResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.CHART)) {
      ChartResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.DASHBOARD)) {
      DashboardResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.MODEL)) {
      ModelResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.TASK)) {
      TaskResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.PIPELINE)) {
      PipelineResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.DATABASE_SERVICE)) {
      DatabaseServiceResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.MESSAGING_SERVICE)) {
      MessagingServiceResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.DASHBOARD_SERVICE)) {
      DashboardServiceResource.addHref(uriInfo, ref);
    } else if (entity.equalsIgnoreCase(Entity.PIPELINE_SERVICE)) {
      PipelineServiceResource.addHref(uriInfo, ref);
    } else {
      throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityTypeNotFound(ref.getType()));
    }
  }

  public static void addHref(UriInfo uriInfo, List<EntityReference> list) {
    Optional.ofNullable(list).orElse(Collections.emptyList()).forEach(ref -> addHref(uriInfo, ref));
  }

  public static void validateUser(UserDAO userDAO, String userId) {
    if (!userDAO.exists(userId)) {
      throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(Entity.USER, userId));
    }
  }

  public static void validateUser(UserDAO3 userDAO, String userId) {
    if (!userDAO.exists(userId)) {
      throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(Entity.USER, userId));
    }
  }

  // Get owner for a given entity
  public static EntityReference populateOwner(UUID id, EntityRelationshipDAO entityRelationshipDAO, UserDAO userDAO,
                                              TeamDAO teamDAO) throws IOException {
    List<EntityReference> ids = entityRelationshipDAO.findFrom(id.toString(), Relationship.OWNS.ordinal());
    if (ids.size() > 1) {
      LOG.warn("Possible database issues - multiple owners {} found for entity {}", ids, id);
    }
    return ids.isEmpty() ? null : EntityUtil.populateOwner(userDAO, teamDAO, ids.get(0));
  }

  // Get owner for a given entity
  public static EntityReference populateOwner(UUID id, EntityRelationshipDAO3 entityRelationshipDAO, UserDAO3 userDAO,
                                              TeamDAO3 teamDAO) throws IOException {
    List<EntityReference> ids = entityRelationshipDAO.findFrom(id.toString(), Relationship.OWNS.ordinal());
    if (ids.size() > 1) {
      LOG.warn("Possible database issues - multiple owners {} found for entity {}", ids, id);
    }
    return ids.isEmpty() ? null : EntityUtil.populateOwner(userDAO, teamDAO, ids.get(0));
  }

  /**
   * For given Owner with Id and Type that can be either team or user,
   * validate Owner ID and return fully populated Owner
   */
  public static EntityReference populateOwner(UserDAO userDAO, TeamDAO teamDAO, EntityReference owner)
          throws IOException {
    if (owner == null) {
      return null;
    }
    String id = owner.getId().toString();
    if (owner.getType().equalsIgnoreCase("user")) {
      User ownerInstance = EntityUtil.validate(id, userDAO.findById(id), User.class);
      owner.setName(ownerInstance.getName());
      if (Optional.ofNullable(ownerInstance.getDeactivated()).orElse(false)) {
        throw new IllegalArgumentException(CatalogExceptionMessage.deactivatedUser(id));
      }
    } else if (owner.getType().equalsIgnoreCase("team")) {
      Team ownerInstance = EntityUtil.validate(id, teamDAO.findById(id), Team.class);
      owner.setDescription(ownerInstance.getDescription());
      owner.setName(ownerInstance.getName());
    } else {
      throw new IllegalArgumentException(String.format("Invalid ownerType %s", owner.getType()));
    }
    return owner;
  }

  public static EntityReference populateOwner(UserDAO3 userDAO, TeamDAO3 teamDAO,
                                              EntityReference owner)
          throws IOException {
    if (owner == null) {
      return null;
    }
    String id = owner.getId().toString();
    if (owner.getType().equalsIgnoreCase("user")) {
      User ownerInstance = userDAO.findEntityById(id);
      owner.setName(ownerInstance.getName());
      if (Optional.ofNullable(ownerInstance.getDeactivated()).orElse(false)) {
        throw new IllegalArgumentException(CatalogExceptionMessage.deactivatedUser(id));
      }
    } else if (owner.getType().equalsIgnoreCase("team")) {
      Team ownerInstance = teamDAO.findEntityById(id);
      owner.setDescription(ownerInstance.getDescription());
      owner.setName(ownerInstance.getName());
    } else {
      throw new IllegalArgumentException(String.format("Invalid ownerType %s", owner.getType()));
    }
    return owner;
  }
  public static void setOwner(EntityRelationshipDAO dao, UUID ownedEntityId, String ownedEntityType,
                              EntityReference owner) {
    // Add relationship owner --- owns ---> ownedEntity
    if (owner != null) {
      LOG.info("Adding owner {}:{} for entity {}", owner.getType(), owner.getId(), ownedEntityId);
      dao.insert(owner.getId().toString(), ownedEntityId.toString(), owner.getType(), ownedEntityType,
              Relationship.OWNS.ordinal());
    }
  }

  public static void setOwner(EntityRelationshipDAO3 dao, UUID ownedEntityId, String ownedEntityType,
                              EntityReference owner) {
    // Add relationship owner --- owns ---> ownedEntity
    if (owner != null) {
      LOG.info("Adding owner {}:{} for entity {}", owner.getType(), owner.getId(), ownedEntityId);
      dao.insert(owner.getId().toString(), ownedEntityId.toString(), owner.getType(), ownedEntityType,
              Relationship.OWNS.ordinal());
    }
  }

  /**
   * Unassign owner relationship for a given entity
   */
  public static void unassignOwner(EntityRelationshipDAO dao, EntityReference owner, String ownedEntityId) {
    if (owner != null && owner.getId() != null) {
      LOG.info("Removing owner {}:{} for entity {}", owner.getType(), owner.getId(),
              ownedEntityId);
      dao.delete(owner.getId().toString(), ownedEntityId, Relationship.OWNS.ordinal());
    }
  }

  /**
   * Unassign owner relationship for a given entity
   */
  public static void unassignOwner(EntityRelationshipDAO3 dao, EntityReference owner, String ownedEntityId) {
    if (owner != null && owner.getId() != null) {
      LOG.info("Removing owner {}:{} for entity {}", owner.getType(), owner.getId(),
              ownedEntityId);
      dao.delete(owner.getId().toString(), ownedEntityId, Relationship.OWNS.ordinal());
    }
  }

  public static void updateOwner(EntityRelationshipDAO dao, EntityReference originalOwner, EntityReference newOwner,
                                 UUID ownedEntityId, String ownedEntityType) {
    // TODO inefficient use replace instead of delete and add?
    // TODO check for orig and new owners being the same
    unassignOwner(dao, originalOwner, ownedEntityId.toString());
    setOwner(dao, ownedEntityId, ownedEntityType, newOwner);
  }

  public static void updateOwner(EntityRelationshipDAO3 dao, EntityReference originalOwner, EntityReference newOwner,
                                 UUID ownedEntityId, String ownedEntityType) {
    // TODO inefficient use replace instead of delete and add?
    // TODO check for orig and new owners being the same
    unassignOwner(dao, originalOwner, ownedEntityId.toString());
    setOwner(dao, ownedEntityId, ownedEntityType, newOwner);
  }

  public static List<EntityReference> getEntityReference(List<EntityReference> list, TableDAO tableDAO,
                                                         DatabaseDAO databaseDAO, MetricsDAO metricsDAO,
                                                         DashboardDAO dashboardDAO, ReportDAO reportDAO,
                                                         TopicDAO topicDAO, ChartDAO chartDAO,
                                                         TaskDAO taskDAO, ModelDAO modelDAO,
                                                         PipelineDAO pipelineDAO) throws IOException {
    for (EntityReference ref : list) {
      getEntityReference(ref, tableDAO, databaseDAO, metricsDAO, dashboardDAO, reportDAO, topicDAO, chartDAO,
              taskDAO, modelDAO, pipelineDAO);
    }
    return list;
  }

  public static List<EntityReference> getEntityReference(List<EntityReference> list, TableDAO3 tableDAO,
                                                         DatabaseDAO3 databaseDAO, MetricsDAO3 metricsDAO,
                                                         DashboardDAO3 dashboardDAO, ReportDAO3 reportDAO,
                                                         TopicDAO3 topicDAO, ChartDAO3 chartDAO,
                                                         TaskDAO3 taskDAO, ModelDAO3 modelDAO,
                                                         PipelineDAO3 pipelineDAO) throws IOException {
    for (EntityReference ref : list) {
      getEntityReference3(ref, tableDAO, databaseDAO, metricsDAO, dashboardDAO, reportDAO, topicDAO, chartDAO,
              taskDAO, modelDAO, pipelineDAO);
    }
    return list;
  }


  public static EntityReference getEntityReference(EntityReference ref, TableDAO tableDAO, DatabaseDAO databaseDAO,
                                                   MetricsDAO metricsDAO, DashboardDAO dashboardDAO,
                                                   ReportDAO reportDAO, TopicDAO topicDAO, ChartDAO chartDAO,
                                                   TaskDAO taskDAO, ModelDAO modelDAO, PipelineDAO pipelineDAO)
          throws IOException {
    // Note href to entity reference is not added here
    String entity = ref.getType();
    String id = ref.getId().toString();
    if (entity.equalsIgnoreCase(Entity.TABLE)) {
      Table instance = EntityUtil.validate(id, tableDAO.findById(id), Table.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.DATABASE)) {
      Database instance = EntityUtil.validate(id, databaseDAO.findById(id), Database.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.METRICS)) {
      Metrics instance = EntityUtil.validate(id, metricsDAO.findById(id), Metrics.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.DASHBOARD)) {
      Dashboard instance = EntityUtil.validate(id, dashboardDAO.findById(id), Dashboard.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.REPORT)) {
      Report instance = EntityUtil.validate(id, reportDAO.findById(id), Report.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.TOPIC)) {
      Topic instance = EntityUtil.validate(id, topicDAO.findById(id), Topic.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.CHART)) {
      Chart instance = EntityUtil.validate(id, chartDAO.findById(id), Chart.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.TASK)) {
      Task instance = EntityUtil.validate(id, taskDAO.findById(id), Task.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.PIPELINE)) {
      Pipeline instance = EntityUtil.validate(id, pipelineDAO.findById(id), Pipeline.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.MODEL)) {
      Model instance = EntityUtil.validate(id, modelDAO.findById(id), Model.class);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    }
    throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityTypeNotFound(entity));
  }

  public static EntityReference getEntityReference3(EntityReference ref, TableDAO3 tableDAO, DatabaseDAO3 databaseDAO,
                                                    MetricsDAO3 metricsDAO, DashboardDAO3 dashboardDAO,
                                                    ReportDAO3 reportDAO, TopicDAO3 topicDAO, ChartDAO3 chartDAO,
                                                    TaskDAO3 taskDAO, ModelDAO3 modelDAO, PipelineDAO3 pipelineDAO)
          throws IOException {
    // Note href to entity reference is not added here
    String entity = ref.getType();
    String id = ref.getId().toString();
    if (entity.equalsIgnoreCase(Entity.TABLE)) {
      Table instance = tableDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.DATABASE)) {
      Database instance = databaseDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.METRICS)) {
      Metrics instance = metricsDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.DASHBOARD)) {
      Dashboard instance = dashboardDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.REPORT)) {
      Report instance = reportDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.TOPIC)) {
      Topic instance = topicDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.CHART)) {
      Chart instance = chartDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.TASK)) {
      Task instance = taskDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.PIPELINE)) {
      Pipeline instance = pipelineDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    } else if (entity.equalsIgnoreCase(Entity.MODEL)) {
      Model instance = modelDAO.findEntityById(id);
      return ref.withDescription(instance.getDescription()).withName(instance.getFullyQualifiedName());
    }
    throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityTypeNotFound(entity));
  }

  public static EntityReference getEntityReference(String entity, UUID id, TableDAO tableDAO, DatabaseDAO databaseDAO,
                                                   MetricsDAO metricsDAO, DashboardDAO dashboardDAO,
                                                   ReportDAO reportDAO, TopicDAO topicDAO, ChartDAO chartDAO,
                                                   TaskDAO taskDAO, ModelDAO modelDAO, PipelineDAO pipelineDAO)
          throws IOException {
    EntityReference ref = new EntityReference().withId(id).withType(entity);
    return getEntityReference(ref, tableDAO, databaseDAO, metricsDAO, dashboardDAO,
            reportDAO, topicDAO, chartDAO, taskDAO, modelDAO, pipelineDAO);
  }

  public static EntityReference getEntityReference(String entity, UUID id, TableDAO3 tableDAO,
                                                   DatabaseDAO3 databaseDAO,
                                                   MetricsDAO3 metricsDAO, DashboardDAO3 dashboardDAO,
                                                   ReportDAO3 reportDAO, TopicDAO3 topicDAO, ChartDAO3 chartDAO,
                                                   TaskDAO3 taskDAO, ModelDAO3 modelDAO, PipelineDAO3 pipelineDAO)
          throws IOException {
    EntityReference ref = new EntityReference().withId(id).withType(entity);
    return getEntityReference3(ref, tableDAO, databaseDAO, metricsDAO, dashboardDAO,
            reportDAO, topicDAO, chartDAO, taskDAO, modelDAO, pipelineDAO);
  }

  public static EntityReference getEntityReferenceByName(String entity, String fqn, TableDAO tableDAO,
                                                         DatabaseDAO databaseDAO, MetricsDAO metricsDAO,
                                                         ReportDAO reportDAO, TopicDAO topicDAO, ChartDAO chartDAO,
                                                         DashboardDAO dashboardDAO, TaskDAO taskDAO, ModelDAO modelDAO,
                                                         PipelineDAO pipelineDAO)
          throws IOException {
    if (entity.equalsIgnoreCase(Entity.TABLE)) {
      Table instance = EntityUtil.validate(fqn, tableDAO.findByFqn(fqn), Table.class);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.DATABASE)) {
      Database instance = EntityUtil.validate(fqn, databaseDAO.findByFQN(fqn), Database.class);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.METRICS)) {
      Metrics instance = EntityUtil.validate(fqn, metricsDAO.findByFQN(fqn), Metrics.class);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.REPORT)) {
      Report instance = EntityUtil.validate(fqn, reportDAO.findByFQN(fqn), Report.class);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.TOPIC)) {
      Topic instance = EntityUtil.validate(fqn, topicDAO.findByFQN(fqn), Topic.class);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.CHART)) {
      Chart instance = EntityUtil.validate(fqn, chartDAO.findByFQN(fqn), Chart.class);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.DASHBOARD)) {
      Dashboard instance = EntityUtil.validate(fqn, dashboardDAO.findByFQN(fqn), Dashboard.class);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.TASK)) {
      Task instance = EntityUtil.validate(fqn, taskDAO.findByFQN(fqn), Task.class);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.PIPELINE)) {
      Pipeline instance = EntityUtil.validate(fqn, pipelineDAO.findByFQN(fqn), Pipeline.class);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.MODEL)) {
      Model instance = EntityUtil.validate(fqn, modelDAO.findByFQN(fqn), Model.class);
      return getEntityReference(instance);
    }
    throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(entity, fqn));
  }

  public static EntityReference getEntityReferenceByName(String entity, String fqn, TableDAO3 tableDAO,
                                                         DatabaseDAO3 databaseDAO, MetricsDAO3 metricsDAO,
                                                         ReportDAO3 reportDAO, TopicDAO3 topicDAO, ChartDAO3 chartDAO,
                                                         DashboardDAO3 dashboardDAO, TaskDAO3 taskDAO, ModelDAO3 modelDAO,
                                                         PipelineDAO3 pipelineDAO)
          throws IOException {
    if (entity.equalsIgnoreCase(Entity.TABLE)) {
      Table instance = tableDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.DATABASE)) {
      Database instance = databaseDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.METRICS)) {
      Metrics instance = metricsDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.REPORT)) {
      Report instance = reportDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.TOPIC)) {
      Topic instance = topicDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.CHART)) {
      Chart instance = chartDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.DASHBOARD)) {
      Dashboard instance = dashboardDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.TASK)) {
      Task instance = taskDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.PIPELINE)) {
      Pipeline instance = pipelineDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    } else if (entity.equalsIgnoreCase(Entity.MODEL)) {
      Model instance = modelDAO.findEntityByName(fqn);
      return getEntityReference(instance);
    }
    throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(entity, fqn));
  }

  public static EntityReference getEntityReference(Object entity, Class<?> clazz) {
    if (clazz.toString().toLowerCase().endsWith(Entity.TABLE.toLowerCase())) {
      Table instance = (Table) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.DATABASE.toLowerCase())) {
      Database instance = (Database) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.METRICS.toLowerCase())) {
      Metrics instance = (Metrics) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.DATABASE_SERVICE.toLowerCase())) {
      DatabaseService instance = (DatabaseService) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.REPORT.toLowerCase())) {
      Report instance = (Report) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.TEAM.toLowerCase())) {
      Team instance = (Team) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.USER.toLowerCase())) {
      User instance = (User) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.TOPIC.toLowerCase())) {
      Topic instance = (Topic) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.CHART.toLowerCase())) {
      Chart instance = (Chart) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.DASHBOARD.toLowerCase())) {
      Dashboard instance = (Dashboard) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.TASK.toLowerCase())) {
      Task instance = (Task) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.MODEL.toLowerCase())) {
      Model instance = (Model) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.PIPELINE.toLowerCase())) {
      Pipeline instance = (Pipeline) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.MESSAGING_SERVICE.toLowerCase())) {
      MessagingService instance = (MessagingService) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.DASHBOARD_SERVICE.toLowerCase())) {
      DashboardService instance = (DashboardService) entity;
      return getEntityReference(instance);
    } else if (clazz.toString().toLowerCase().endsWith(Entity.PIPELINE_SERVICE.toLowerCase())) {
      PipelineService instance = (PipelineService) entity;
      return getEntityReference(instance);
    }
    throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityTypeNotFound(
            String.format("Failed to find entity class %s", clazz.toString())));
  }

  public static EntityReference getEntityReference(DatabaseService service) {
    return new EntityReference().withName(service.getName()).withId(service.getId())
            .withType(Entity.DATABASE_SERVICE);
  }

  public static EntityReference getEntityReference(MessagingService service) {
    return new EntityReference().withName(service.getName()).withId(service.getId())
            .withType(Entity.MESSAGING_SERVICE);
  }

  public static EntityReference getEntityReference(DashboardService service) {
    return new EntityReference().withName(service.getName()).withId(service.getId())
            .withType(Entity.DASHBOARD_SERVICE);
  }

  public static EntityReference getEntityReference(PipelineService service) {
    return new EntityReference().withName(service.getName()).withId(service.getId())
            .withType(Entity.PIPELINE_SERVICE);
  }

  public static EntityReference validateEntityLink(EntityLink entityLink, UserDAO userDAO, TeamDAO teamDAO,
                                                   TableDAO3 tableDAO, DatabaseDAO databaseDAO, MetricsDAO metricsDAO,
                                                   DashboardDAO dashboardDAO, ReportDAO reportDAO, TopicDAO topicDAO,
                                                   TaskDAO taskDAO, ModelDAO modelDAO, PipelineDAO pipelineDAO)
          throws IOException {
    String entityType = entityLink.getEntityType();
    String fqn = entityLink.getEntityId();
    if (entityType.equalsIgnoreCase(Entity.USER)) {
      return getEntityReference(EntityUtil.validate(fqn, userDAO.findByName(fqn), User.class));
    } else if (entityType.equalsIgnoreCase(Entity.TEAM)) {
      return getEntityReference(EntityUtil.validate(fqn, teamDAO.findByName(fqn), Team.class));
    } else if (entityType.equalsIgnoreCase(Entity.TABLE)) {
      return getEntityReference(tableDAO.findEntityByName(fqn));
    } else if (entityType.equalsIgnoreCase(Entity.DATABASE)) {
      return getEntityReference(EntityUtil.validate(fqn, databaseDAO.findByFQN(fqn), Database.class));
    } else if (entityType.equalsIgnoreCase(Entity.METRICS)) {
      return getEntityReference(EntityUtil.validate(fqn, metricsDAO.findByFQN(fqn), Metrics.class));
    } else if (entityType.equalsIgnoreCase(Entity.DASHBOARD)) {
      return getEntityReference(EntityUtil.validate(fqn, dashboardDAO.findByFQN(fqn), Dashboard.class));
    } else if (entityType.equalsIgnoreCase(Entity.REPORT)) {
      return getEntityReference(EntityUtil.validate(fqn, reportDAO.findByFQN(fqn), Report.class));
    } else if (entityType.equalsIgnoreCase(Entity.TOPIC)) {
      return getEntityReference(EntityUtil.validate(fqn, topicDAO.findByFQN(fqn), Topic.class));
    } else if (entityType.equalsIgnoreCase(Entity.TASK)) {
      return getEntityReference(EntityUtil.validate(fqn, taskDAO.findByFQN(fqn), Task.class));
    } else if (entityType.equalsIgnoreCase(Entity.PIPELINE)) {
      return getEntityReference(EntityUtil.validate(fqn, pipelineDAO.findByFQN(fqn), Pipeline.class));
    } else if (entityType.equalsIgnoreCase(Entity.MODEL)) {
      return getEntityReference(EntityUtil.validate(fqn, modelDAO.findByFQN(fqn), Model.class));
    } else {
      throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(entityType, fqn));
    }
  }

  public static EntityReference validateEntityLink(EntityLink entityLink, UserDAO userDAO, TeamDAO teamDAO,
                                                   TableDAO tableDAO, DatabaseDAO databaseDAO, MetricsDAO metricsDAO,
                                                   DashboardDAO dashboardDAO, ReportDAO reportDAO, TopicDAO topicDAO,
                                                   TaskDAO taskDAO, ModelDAO modelDAO, PipelineDAO pipelineDAO)
          throws IOException {
    String entityType = entityLink.getEntityType();
    String fqn = entityLink.getEntityId();
    if (entityType.equalsIgnoreCase(Entity.USER)) {
      return getEntityReference(EntityUtil.validate(fqn, userDAO.findByName(fqn), User.class));
    } else if (entityType.equalsIgnoreCase(Entity.TEAM)) {
      return getEntityReference(EntityUtil.validate(fqn, teamDAO.findByName(fqn), Team.class));
    } else if (entityType.equalsIgnoreCase(Entity.TABLE)) {
      return getEntityReference(EntityUtil.validate(fqn, tableDAO.findByFqn(fqn), Table.class));
    } else if (entityType.equalsIgnoreCase(Entity.DATABASE)) {
      return getEntityReference(EntityUtil.validate(fqn, databaseDAO.findByFQN(fqn), Database.class));
    } else if (entityType.equalsIgnoreCase(Entity.METRICS)) {
      return getEntityReference(EntityUtil.validate(fqn, metricsDAO.findByFQN(fqn), Metrics.class));
    } else if (entityType.equalsIgnoreCase(Entity.DASHBOARD)) {
      return getEntityReference(EntityUtil.validate(fqn, dashboardDAO.findByFQN(fqn), Dashboard.class));
    } else if (entityType.equalsIgnoreCase(Entity.REPORT)) {
      return getEntityReference(EntityUtil.validate(fqn, reportDAO.findByFQN(fqn), Report.class));
    } else if (entityType.equalsIgnoreCase(Entity.TOPIC)) {
      return getEntityReference(EntityUtil.validate(fqn, topicDAO.findByFQN(fqn), Topic.class));
    } else if (entityType.equalsIgnoreCase(Entity.TASK)) {
      return getEntityReference(EntityUtil.validate(fqn, taskDAO.findByFQN(fqn), Task.class));
    } else if (entityType.equalsIgnoreCase(Entity.PIPELINE)) {
      return getEntityReference(EntityUtil.validate(fqn, pipelineDAO.findByFQN(fqn), Pipeline.class));
    } else if (entityType.equalsIgnoreCase(Entity.MODEL)) {
      return getEntityReference(EntityUtil.validate(fqn, modelDAO.findByFQN(fqn), Model.class));
    } else {
      throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(entityType, fqn));
    }
  }

  public static UsageDetails getLatestUsage(UsageDAO usageDAO, UUID entityId) {
    LOG.debug("Getting latest usage for {}", entityId);
    UsageDetails details = usageDAO.getLatestUsage(entityId.toString());
    if (details == null) {
      LOG.debug("Usage details not found. Sending default usage");
      UsageStats stats = new UsageStats().withCount(0).withPercentileRank(0.0);
      details = new UsageDetails().withDailyStats(stats).withWeeklyStats(stats).withMonthlyStats(stats)
              .withDate(RestUtil.DATE_FORMAT.format(new Date()));
    }
    return details;
  }

  public static UsageDetails getLatestUsage(UsageDAO3 usageDAO, UUID entityId) {
    LOG.debug("Getting latest usage for {}", entityId);
    UsageDetails details = usageDAO.getLatestUsage(entityId.toString());
    if (details == null) {
      LOG.debug("Usage details not found. Sending default usage");
      UsageStats stats = new UsageStats().withCount(0).withPercentileRank(0.0);
      details = new UsageDetails().withDailyStats(stats).withWeeklyStats(stats).withMonthlyStats(stats)
              .withDate(RestUtil.DATE_FORMAT.format(new Date()));
    }
    return details;
  }

  public static EntityReference getEntityReference(Pipeline pipeline) {
    return new EntityReference().withDescription(pipeline.getDescription()).withId(pipeline.getId())
            .withName(pipeline.getFullyQualifiedName()).withType(Entity.PIPELINE);
  }

  public static EntityReference getEntityReference(Task task) {
    return new EntityReference().withDescription(task.getDescription()).withId(task.getId())
            .withName(task.getFullyQualifiedName()).withType(Entity.TASK);
  }

  public static EntityReference getEntityReference(Model model) {
    return new EntityReference().withDescription(model.getDescription()).withId(model.getId())
            .withName(model.getFullyQualifiedName()).withType(Entity.MODEL);
  }

  public static EntityReference getEntityReference(Chart chart) {
    return new EntityReference().withDescription(chart.getDescription()).withId(chart.getId())
            .withName(chart.getFullyQualifiedName()).withType(Entity.CHART);
  }

  public static EntityReference getEntityReference(Topic topic) {
    return new EntityReference().withDescription(topic.getDescription()).withId(topic.getId())
            .withName(topic.getFullyQualifiedName()).withType(Entity.TOPIC);
  }

  public static EntityReference getEntityReference(Database database) {
    return new EntityReference().withDescription(database.getDescription()).withId(database.getId())
            .withName(database.getFullyQualifiedName()).withType(Entity.DATABASE);
  }

  public static EntityReference getEntityReference(Table table) {
    return new EntityReference().withDescription(table.getDescription()).withId(table.getId())
            .withName(table.getFullyQualifiedName()).withType(Entity.TABLE);
  }

  public static EntityReference getEntityReference(Report report) {
    return new EntityReference().withDescription(report.getDescription()).withId(report.getId())
            .withName(report.getFullyQualifiedName()).withType(Entity.REPORT);
  }

  public static EntityReference getEntityReference(Metrics metrics) {
    return new EntityReference().withDescription(metrics.getDescription()).withId(metrics.getId())
            .withName(metrics.getFullyQualifiedName()).withType(Entity.METRICS);
  }

  public static EntityReference getEntityReference(Dashboard dashboard) {
    return new EntityReference().withDescription(dashboard.getDescription()).withId(dashboard.getId())
            .withName(dashboard.getFullyQualifiedName()).withType(Entity.DASHBOARD);
  }

  public static EntityReference getEntityReference(Team team) {
    return new EntityReference().withDescription(team.getDescription()).withId(team.getId())
            .withName(team.getName()).withType(Entity.TEAM);
  }

  public static EntityReference getEntityReference(User user) {
    return new EntityReference().withDescription(user.getDisplayName()).withId(user.getId())
            .withName(user.getName()).withType(Entity.USER);
  }


  public static void validateTags(TagDAO tagDAO, List<TagLabel> tagLabels) {
    Optional.ofNullable(tagLabels).orElse(Collections.emptyList()).forEach(tagLabel -> {
      if (!tagDAO.tagExists(tagLabel.getTagFQN())) {
        throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(Tag.class.getSimpleName(),
                tagLabel.getTagFQN()));
      }
    });
  }


  /**
   * Apply tags {@code tagLabels} to the entity or field identified by {@code targetFQN}
   */
  public static void applyTags(TagDAO tagDAO, List<TagLabel> tagLabels, String targetFQN) throws IOException {
    for (TagLabel tagLabel : Optional.ofNullable(tagLabels).orElse(Collections.emptyList())) {
      String json = tagDAO.findTag(tagLabel.getTagFQN());
      if (json == null) {
        // Invalid TagLabel
        throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(Tag.class.getSimpleName(),
                tagLabel.getTagFQN()));
      }
      Tag tag = JsonUtils.readValue(json, Tag.class);

      // Apply tagLabel to targetFQN that identifies an entity or field
      tagDAO.applyTag(tagLabel.getTagFQN(), targetFQN, tagLabel.getLabelType().ordinal(),
              tagLabel.getState().ordinal());

      // Apply derived tags
      List<TagLabel> derivedTags = getDerivedTags(tagLabel, tag);
      applyTags(tagDAO, derivedTags, targetFQN);
    }
  }

  /**
   * Apply tags {@code tagLabels} to the entity or field identified by {@code targetFQN}
   */
  public static void applyTags(TagDAO3 tagDAO, List<TagLabel> tagLabels, String targetFQN) throws IOException {
    for (TagLabel tagLabel : Optional.ofNullable(tagLabels).orElse(Collections.emptyList())) {
      String json = tagDAO.findTag(tagLabel.getTagFQN());
      if (json == null) {
        // Invalid TagLabel
        throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(Tag.class.getSimpleName(),
                tagLabel.getTagFQN()));
      }
      Tag tag = JsonUtils.readValue(json, Tag.class);

      // Apply tagLabel to targetFQN that identifies an entity or field
      tagDAO.applyTag(tagLabel.getTagFQN(), targetFQN, tagLabel.getLabelType().ordinal(),
              tagLabel.getState().ordinal());

      // Apply derived tags
      List<TagLabel> derivedTags = getDerivedTags(tagLabel, tag);
      applyTags(tagDAO, derivedTags, targetFQN);
    }
  }
  public static List<TagLabel> getDerivedTags(TagLabel tagLabel, Tag tag) {
    List<TagLabel> derivedTags = new ArrayList<>();
    for (String fqn : Optional.ofNullable(tag.getAssociatedTags()).orElse(Collections.emptyList())) {
      derivedTags.add(new TagLabel().withTagFQN(fqn).withState(tagLabel.getState()).withLabelType(LabelType.DERIVED));
    }
    return derivedTags;
  }

  /**
   * Validate given list of tags and add derived tags to it
   */
  public static List<TagLabel> addDerivedTags(TagDAO tagDAO, List<TagLabel> tagLabels) throws IOException {
    List<TagLabel> updatedTagLabels = new ArrayList<>();
    for (TagLabel tagLabel : Optional.ofNullable(tagLabels).orElse(Collections.emptyList())) {
      String json = tagDAO.findTag(tagLabel.getTagFQN());
      if (json == null) {
        // Invalid TagLabel
        throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(Tag.class.getSimpleName(),
                tagLabel.getTagFQN()));
      }
      Tag tag = JsonUtils.readValue(json, Tag.class);
      updatedTagLabels.add(tagLabel);

      // Apply derived tags
      List<TagLabel> derivedTags = getDerivedTags(tagLabel, tag);
      updatedTagLabels = EntityUtil.mergeTags(updatedTagLabels, derivedTags);
    }
    return updatedTagLabels;
  }

  /**
   * Validate given list of tags and add derived tags to it
   */
  public static List<TagLabel> addDerivedTags(TagDAO3 tagDAO, List<TagLabel> tagLabels) throws IOException {
    List<TagLabel> updatedTagLabels = new ArrayList<>();
    for (TagLabel tagLabel : Optional.ofNullable(tagLabels).orElse(Collections.emptyList())) {
      String json = tagDAO.findTag(tagLabel.getTagFQN());
      if (json == null) {
        // Invalid TagLabel
        throw EntityNotFoundException.byMessage(CatalogExceptionMessage.entityNotFound(Tag.class.getSimpleName(),
                tagLabel.getTagFQN()));
      }
      Tag tag = JsonUtils.readValue(json, Tag.class);
      updatedTagLabels.add(tagLabel);

      // Apply derived tags
      List<TagLabel> derivedTags = getDerivedTags(tagLabel, tag);
      updatedTagLabels = EntityUtil.mergeTags(updatedTagLabels, derivedTags);
    }
    return updatedTagLabels;
  }
  public static void removeTags(TagDAO tagDAO, String fullyQualifiedName) {
    tagDAO.deleteTags(fullyQualifiedName);
  }

  public static void removeTagsByPrefix(TagDAO tagDAO, String fullyQualifiedName) {
    tagDAO.deleteTagsByPrefix(fullyQualifiedName);
  }

  public static void removeTagsByPrefix(TagDAO3 tagDAO, String fullyQualifiedName) {
    tagDAO.deleteTagsByPrefix(fullyQualifiedName);
  }

  public static List<TagLabel> mergeTags(List<TagLabel> list1, List<TagLabel> list2) {
    List<TagLabel> mergedTags = Stream.concat(Optional.ofNullable(list1).orElse(Collections.emptyList()).stream(),
            Optional.ofNullable(list2).orElse(Collections.emptyList()).stream())
            .distinct().collect(Collectors.toList());
    return mergedTags.isEmpty() ? null : mergedTags;
  }

  public static void publishEntityCreatedEvent(String entity, String entityName, String event) {
    String print = String.format("Entity Created: [%s] Name: [%s] Event: [%s]", entity, entityName, event);
    LOG.info(print);
  }

  public static void publishEntityUpdatedEvent(String entity,
                                               String entityName,
                                               String oldEvent,
                                               String newEvent) {
    String diff = JsonUtils.diffTwoJson(oldEvent, newEvent);
    String print = String.format("Entity Updated: [%s] Name: [%s] DiffString: [%s]", entity, entityName, diff);
    LOG.info(print);
  }

  public static boolean addFollower(EntityRelationshipDAO dao, UserDAO userDAO, String followedEntityId,
                                    String followedEntityType, String followerId, String followerEntity)
          throws IOException {
    User user = EntityUtil.validate(followerId, userDAO.findById(followerId), User.class);
    if (Optional.ofNullable(user.getDeactivated()).orElse(false)) {
      throw new IllegalArgumentException(CatalogExceptionMessage.deactivatedUser(followerId));
    }
    return dao.insert(followerId, followedEntityId, followerEntity, followedEntityType,
            Relationship.FOLLOWS.ordinal()) > 0;
  }

  public static boolean addFollower(EntityRelationshipDAO3 dao, UserDAO3 userDAO,
                                    String followedEntityId,
                                    String followedEntityType, String followerId, String followerEntity)
          throws IOException {
    User user = userDAO.findEntityById(followerId);
    if (Optional.ofNullable(user.getDeactivated()).orElse(false)) {
      throw new IllegalArgumentException(CatalogExceptionMessage.deactivatedUser(followerId));
    }
    return dao.insert(followerId, followedEntityId, followerEntity, followedEntityType,
            Relationship.FOLLOWS.ordinal()) > 0;
  }

  public static void removeFollower(EntityRelationshipDAO dao, String followedEntityId, String followerId) {
    dao.delete(followerId, followedEntityId, Relationship.FOLLOWS.ordinal());
  }

  public static void removeFollower(EntityRelationshipDAO3 dao, String followedEntityId, String followerId) {
    dao.delete(followerId, followedEntityId, Relationship.FOLLOWS.ordinal());
  }

  public static List<EntityReference> getFollowers(UUID followedEntityId, EntityRelationshipDAO entityRelationshipDAO,
                                                   UserDAO userDAO) throws IOException {
    List<String> followerIds = entityRelationshipDAO.findFrom(followedEntityId.toString(),
            Relationship.FOLLOWS.ordinal(),
            Entity.USER);
    List<EntityReference> followers = new ArrayList<>();
    for (String followerId : followerIds) {
      User user = EntityUtil.validate(followerId, userDAO.findById(followerId), User.class);
      followers.add(new EntityReference().withName(user.getName()).withId(user.getId()).withType("user"));
    }
    return followers;
  }

  public static List<EntityReference> getFollowers(UUID followedEntityId, EntityRelationshipDAO3 entityRelationshipDAO,
                                                   UserDAO3 userDAO) throws IOException {
    List<String> followerIds = entityRelationshipDAO.findFrom(followedEntityId.toString(),
            Relationship.FOLLOWS.ordinal(),
            Entity.USER);
    List<EntityReference> followers = new ArrayList<>();
    for (String followerId : followerIds) {
      User user = userDAO.findEntityById(followerId);
      followers.add(new EntityReference().withName(user.getName()).withId(user.getId()).withType("user"));
    }
    return followers;
  }
  public static class Fields {
    private final List<String> fieldList;

    public Fields(List<String> validFields, String fieldsParam) {
      if (fieldsParam == null) {
        fieldList = Collections.emptyList();
        return;
      }
      fieldList = Arrays.asList(fieldsParam.replaceAll("\\s", "").split(","));
      for (String field : fieldList) {
        if (!validFields.contains(field)) {
          throw new IllegalArgumentException(CatalogExceptionMessage.invalidField(field));
        }
      }
    }

    public boolean contains(String field) {
      return fieldList.contains(field);
    }
  }

  public static <T> ResultList<T> listAfter(EntityRepository<T> dao, Class<T> clz, Fields fields, String prefixFqn,
                                            int limitParam, String after)
          throws IOException, ParseException, GeneralSecurityException {
    // forward scrolling, if after == null then first page is being asked
    List<String> jsons = dao.listAfter(prefixFqn, limitParam + 1, after == null ? "" :
            CipherText.instance().decrypt(after));

    List<T> entities = new ArrayList<>();
    for (String json : jsons) {
      entities.add(dao.setFields(JsonUtils.readValue(json, clz), fields));
    }
    int total = dao.listCount(prefixFqn);

    String beforeCursor, afterCursor = null;
    beforeCursor = after == null ? null : dao.getFullyQualifiedName(entities.get(0));
    if (entities.size() > limitParam) { // If extra result exists, then next page exists - return after cursor
      entities.remove(limitParam);
      afterCursor = dao.getFullyQualifiedName(entities.get(limitParam - 1));
    }
    return dao.getResultList(entities, beforeCursor, afterCursor, total);
  }

  public static <T> ResultList<T> listBefore(EntityRepository<T> dao, Class<T> clz, Fields fields, String databaseFQN,
                                             int limitParam, String before)
          throws IOException, ParseException, GeneralSecurityException {
    // Reverse scrolling - Get one extra result used for computing before cursor
    List<String> jsons = dao.listBefore(databaseFQN, limitParam + 1, CipherText.instance().decrypt(before));

    List<T> entities = new ArrayList<>();
    for (String json : jsons) {
      entities.add(dao.setFields(JsonUtils.readValue(json, clz), fields));
    }
    int total = dao.listCount(databaseFQN);

    String beforeCursor = null, afterCursor;
    if (entities.size() > limitParam) { // If extra result exists, then previous page exists - return before cursor
      entities.remove(0);
      beforeCursor = dao.getFullyQualifiedName(entities.get(0));
    }
    afterCursor = dao.getFullyQualifiedName(entities.get(entities.size() - 1));
    return dao.getResultList(entities, beforeCursor, afterCursor, total);
  }

  public static List<UUID> getIDList(List<EntityReference> refList) {
    if (refList == null) {
      return null;
    }
    return refList.stream().sorted(Comparator.comparing(EntityReference::getId)).map(EntityReference::getId)
            .collect(Collectors.toList());
  }

  public static List<EntityReference> toEntityReference(List<Chart> charts) {
    if (charts == null) {
      return null;
    }
    List<EntityReference> refList = new ArrayList<>();
    for (Chart chart: charts) {
      refList.add(EntityUtil.getEntityReference(chart));
    }
    return refList;
  }
}
