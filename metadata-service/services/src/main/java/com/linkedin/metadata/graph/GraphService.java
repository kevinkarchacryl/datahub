package com.linkedin.metadata.graph;

import com.linkedin.common.urn.Urn;
import com.linkedin.metadata.aspect.models.graph.Edge;
import com.linkedin.metadata.aspect.models.graph.EdgeUrnType;
import com.linkedin.metadata.aspect.models.graph.RelatedEntitiesScrollResult;
import com.linkedin.metadata.models.registry.LineageRegistry;
import com.linkedin.metadata.query.filter.Filter;
import com.linkedin.metadata.query.filter.RelationshipDirection;
import com.linkedin.metadata.query.filter.RelationshipFilter;
import com.linkedin.metadata.query.filter.SortCriterion;
import com.linkedin.metadata.search.utils.QueryUtils;
import io.datahubproject.metadata.context.OperationContext;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.collections.CollectionUtils;

public interface GraphService {
  /** Return lineage registry to construct graph index */
  LineageRegistry getLineageRegistry();

  /**
   * Adds an edge to the graph. This creates the source and destination nodes, if they do not exist.
   */
  void addEdge(final Edge edge);

  /**
   * Adds or updates an edge to the graph. This creates the source and destination nodes, if they do
   * not exist.
   */
  void upsertEdge(final Edge edge);

  /**
   * Remove an edge from the graph.
   *
   * @param edge the edge to delete
   */
  void removeEdge(final Edge edge);

  /**
   * Find related entities (nodes) connected to a source entity via edges of given relationship
   * types. Related entities can be filtered by source and destination type (use `null` for any
   * type), by source and destination entity filter and relationship filter. Pagination of the
   * result is controlled via `offset` and `count`.
   *
   * <p>Starting from a node as the source entity, determined by `sourceType` and
   * `sourceEntityFilter`, related entities are found along the direction of edges
   * (`RelationshipDirection.OUTGOING`) or in opposite direction of edges
   * (`RelationshipDirection.INCOMING`). The destination entities are further filtered by
   * `destinationType` and `destinationEntityFilter`, and then returned as related entities.
   *
   * <p>This does not return duplicate related entities, even if entities are connected to source
   * entities via multiple edges. An empty list of relationship types returns an empty result.
   *
   * <p>In other words, the source and destination entity is not to be understood as the source and
   * destination of the edge, but as the source and destination of "finding related entities", where
   * always the destination entities are returned. This understanding is important when it comes to
   * `RelationshipDirection.INCOMING`. The origin of the edge becomes the destination entity and the
   * source entity is where the edge points to.
   *
   * <p>Example I: dataset one --DownstreamOf-> dataset two --DownstreamOf-> dataset three
   *
   * <p>findRelatedEntities(null, EMPTY_FILTER, null, EMPTY_FILTER, ["DownstreamOf"],
   * RelationshipFilter.setDirection(RelationshipDirection.OUTGOING), 0, 100) -
   * RelatedEntity("DownstreamOf", "dataset two") - RelatedEntity("DownstreamOf", "dataset three")
   *
   * <p>findRelatedEntities(null, EMPTY_FILTER, null, EMPTY_FILTER, ["DownstreamOf"],
   * RelationshipFilter.setDirection(RelationshipDirection.INCOMING), 0, 100) -
   * RelatedEntity("DownstreamOf", "dataset one") - RelatedEntity("DownstreamOf", "dataset two")
   *
   * <p>Example II: dataset one --HasOwner-> user one
   *
   * <p>findRelatedEntities(null, EMPTY_FILTER, null, EMPTY_FILTER, ["HasOwner"],
   * RelationshipFilter.setDirection(RelationshipDirection.OUTGOING), 0, 100) -
   * RelatedEntity("HasOwner", "user one")
   *
   * <p>findRelatedEntities(null, EMPTY_FILTER, null, EMPTY_FILTER, ["HasOwner"],
   * RelationshipFilter.setDirection(RelationshipDirection.INCOMING), 0, 100) -
   * RelatedEntity("HasOwner", "dataset one")
   *
   * <p>Calling this method with {@link RelationshipDirection} `UNDIRECTED` in `relationshipFilter`
   * is equivalent to the union of `OUTGOING` and `INCOMING` (without duplicates).
   *
   * <p>Example III: findRelatedEntities(null, EMPTY_FILTER, null, EMPTY_FILTER, ["DownstreamOf"],
   * RelationshipFilter.setDirection(RelationshipDirection.UNDIRECTED), 0, 100) -
   * RelatedEntity("DownstreamOf", "dataset one") - RelatedEntity("DownstreamOf", "dataset two") -
   * RelatedEntity("DownstreamOf", "dataset three")
   */
  default @Nonnull RelatedEntitiesResult findRelatedEntities(
      @Nonnull final OperationContext opContext,
      @Nullable final Set<String> sourceTypes,
      @Nonnull final Filter sourceEntityFilter,
      @Nullable final Set<String> destinationTypes,
      @Nonnull final Filter destinationEntityFilter,
      @Nonnull final Set<String> relationshipTypes,
      @Nonnull final RelationshipFilter relationshipFilter,
      final int offset,
      final int count) {
    return findRelatedEntities(
        opContext,
        new GraphFilters(
            sourceEntityFilter,
            destinationEntityFilter,
            sourceTypes,
            destinationTypes,
            relationshipTypes,
            relationshipFilter),
        offset,
        count);
  }

  /**
   * Same as above with consolidated input parameter
   *
   * @param opContext
   * @param graphFilters see method above
   * @param offset
   * @param count
   * @return
   */
  @Nonnull
  RelatedEntitiesResult findRelatedEntities(
      @Nonnull final OperationContext opContext,
      @Nonnull final GraphFilters graphFilters,
      final int offset,
      final int count);

  /**
   * Traverse from the entityUrn towards the input direction up to maxHops number of hops Abstracts
   * away the concept of relationship types
   *
   * <p>Unless overridden, it uses the lineage registry to fetch valid edge types and queries for
   * them
   */
  @Nonnull
  default EntityLineageResult getLineage(
      @Nonnull final OperationContext opContext,
      @Nonnull Urn entityUrn,
      @Nonnull LineageDirection direction,
      int offset,
      int count,
      int maxHops) {
    return getLineage(
        opContext,
        entityUrn,
        LineageGraphFilters.forEntityType(
            getLineageRegistry(), entityUrn.getEntityType(), direction),
        offset,
        count,
        maxHops);
  }

  /**
   * Note: Only used by Dgraph Traverse from the entityUrn towards the input direction up to maxHops
   * number of hops. If entityTypes is not empty, will only return edges to entities that are within
   * the entity types set. Abstracts away the concept of relationship types
   *
   * <p>Unless overridden, it uses the lineage registry to fetch valid edge types and queries for
   * them
   */
  @Nonnull
  default EntityLineageResult getLineage(
      @Nonnull final OperationContext opContext,
      @Nonnull Urn entityUrn,
      @Nonnull LineageGraphFilters graphFilters,
      int offset,
      int count,
      int maxHops) {
    if (maxHops > 1) {
      maxHops = 1;
    }
    Set<LineageRegistry.EdgeInfo> edgesToFetch =
        graphFilters.getEdgeInfo(getLineageRegistry(), entityUrn.getEntityType());

    Map<Boolean, List<LineageRegistry.EdgeInfo>> edgesByDirection =
        edgesToFetch.stream()
            .collect(
                Collectors.partitioningBy(
                    edgeInfo -> edgeInfo.getDirection() == RelationshipDirection.OUTGOING));

    EntityLineageResult result =
        new EntityLineageResult()
            .setStart(offset)
            .setCount(count)
            .setRelationships(new LineageRelationshipArray())
            .setTotal(0);
    Set<String> visitedUrns = new HashSet<>();

    // Outgoing edges
    if (!CollectionUtils.isEmpty(edgesByDirection.get(true))) {
      Set<String> relationshipTypes =
          edgesByDirection.get(true).stream()
              .map(LineageRegistry.EdgeInfo::getType)
              .collect(Collectors.toSet());
      // Fetch outgoing edges
      RelatedEntitiesResult outgoingEdges =
          findRelatedEntities(
              opContext,
              null,
              QueryUtils.newFilter("urn", entityUrn.toString()),
              graphFilters.getAllowedEntityTypes(),
              QueryUtils.EMPTY_FILTER,
              relationshipTypes,
              QueryUtils.newRelationshipFilter(
                  QueryUtils.EMPTY_FILTER, RelationshipDirection.OUTGOING),
              offset,
              count);

      // Update offset and count to fetch the correct number of incoming edges below
      offset = Math.max(0, offset - outgoingEdges.getTotal());
      count = Math.max(0, count - outgoingEdges.getEntities().size());

      result.setTotal(result.getTotal() + outgoingEdges.getTotal());
      outgoingEdges
          .getEntities()
          .forEach(
              entity -> {
                visitedUrns.add(entity.getUrn());
                try {
                  result
                      .getRelationships()
                      .add(
                          new LineageRelationship()
                              .setEntity(Urn.createFromString(entity.getUrn()))
                              .setType(entity.getRelationshipType()));
                } catch (URISyntaxException ignored) {
                }
              });
    }

    // Incoming edges
    if (!CollectionUtils.isEmpty(edgesByDirection.get(false))) {
      Set<String> relationshipTypes =
          edgesByDirection.get(false).stream()
              .map(LineageRegistry.EdgeInfo::getType)
              .collect(Collectors.toSet());
      RelatedEntitiesResult incomingEdges =
          findRelatedEntities(
              opContext,
              null,
              QueryUtils.newFilter("urn", entityUrn.toString()),
              graphFilters.getAllowedEntityTypes(),
              QueryUtils.EMPTY_FILTER,
              relationshipTypes,
              QueryUtils.newRelationshipFilter(
                  QueryUtils.EMPTY_FILTER, RelationshipDirection.INCOMING),
              offset,
              count);
      result.setTotal(result.getTotal() + incomingEdges.getTotal());
      incomingEdges
          .getEntities()
          .forEach(
              entity -> {
                if (visitedUrns.contains(entity.getUrn())) {
                  return;
                }
                visitedUrns.add(entity.getUrn());
                try {
                  result
                      .getRelationships()
                      .add(
                          new LineageRelationship()
                              .setEntity(Urn.createFromString(entity.getUrn()))
                              .setType(entity.getRelationshipType()));
                } catch (URISyntaxException ignored) {
                }
              });
    }

    return result;
  }

  /**
   * Removes the given node (if it exists) as well as all edges (incoming and outgoing) of the node.
   */
  void removeNode(@Nonnull final OperationContext opContext, @Nonnull final Urn urn);

  /**
   * Removes edges of the given relationship types from the given node after applying the
   * relationship filter.
   *
   * <p>An empty list of relationship types removes nothing from the node.
   *
   * <p>Calling this method with a {@link RelationshipDirection} `UNDIRECTED` in
   * `relationshipFilter` is equivalent to the union of `OUTGOING` and `INCOMING` (without
   * duplicates).
   */
  void removeEdgesFromNode(
      @Nonnull final OperationContext opContext,
      @Nonnull final Urn urn,
      @Nonnull final Set<String> relationshipTypes,
      @Nonnull final RelationshipFilter relationshipFilter);

  default void configure() {}

  /** Removes all edges and nodes from the graph. */
  void clear();

  /** Whether or not this graph service supports multi-hop */
  default boolean supportsMultiHop() {
    return false;
  }

  /**
   * Set the soft-delete status for the given Urn
   *
   * @param urn URN's status to be set
   * @param removed the removed status
   * @param edgeUrnTypes which URNs to update (source, destination, lifecycleOwner, etc)
   */
  default void setEdgeStatus(
      @Nonnull Urn urn, boolean removed, @Nonnull EdgeUrnType... edgeUrnTypes) {}

  /**
   * Access graph edges
   *
   * @param sourceTypes
   * @param sourceEntityFilter
   * @param destinationTypes
   * @param destinationEntityFilter
   * @param relationshipTypes
   * @param relationshipFilter
   * @param sortCriteria
   * @param scrollId
   * @param count
   * @param startTimeMillis
   * @param endTimeMillis
   * @return
   */
  @Nonnull
  default RelatedEntitiesScrollResult scrollRelatedEntities(
      @Nonnull OperationContext opContext,
      @Nullable Set<String> sourceTypes,
      @Nonnull Filter sourceEntityFilter,
      @Nullable Set<String> destinationTypes,
      @Nonnull Filter destinationEntityFilter,
      @Nonnull Set<String> relationshipTypes,
      @Nonnull RelationshipFilter relationshipFilter,
      @Nonnull List<SortCriterion> sortCriteria,
      @Nullable String scrollId,
      int count,
      @Nullable Long startTimeMillis,
      @Nullable Long endTimeMillis) {
    return scrollRelatedEntities(
        opContext,
        new GraphFilters(
            sourceEntityFilter,
            destinationEntityFilter,
            sourceTypes,
            destinationTypes,
            relationshipTypes,
            relationshipFilter),
        sortCriteria,
        scrollId,
        count,
        startTimeMillis,
        endTimeMillis);
  }

  @Nonnull
  RelatedEntitiesScrollResult scrollRelatedEntities(
      @Nonnull OperationContext opContext,
      @Nonnull GraphFilters graphFilters,
      @Nonnull List<SortCriterion> sortCriteria,
      @Nullable String scrollId,
      int count,
      @Nullable Long startTimeMillis,
      @Nullable Long endTimeMillis);
}
