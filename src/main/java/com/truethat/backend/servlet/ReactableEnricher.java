package com.truethat.backend.servlet;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.PropertyProjection;
import com.google.appengine.api.datastore.Query;
import com.truethat.backend.common.Util;
import com.truethat.backend.model.Emotion;
import com.truethat.backend.model.EventType;
import com.truethat.backend.model.Reactable;
import com.truethat.backend.model.ReactableEvent;
import com.truethat.backend.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Proudly created by ohad on 29/06/2017.
 */
class ReactableEnricher {
  private static final DatastoreService DATASTORE_SERVICE =
      DatastoreServiceFactory.getDatastoreService();

  /**
   * Enriches {@link Reactable}s with data of {@link User} and {@link ReactableEvent}s.
   *
   * @param reactables to enrich
   * @param user       for which to enrich the reactables.
   */
  static void enrich(List<Reactable> reactables, User user) {
    enrichUsers(reactables);
    enrichEvents(reactables, user);
  }

  /**
   * Enriches {@link Reactable}s with data of {@link Reactable#director} first and last names.
   *
   * @param reactables to enrich
   */
  private static void enrichUsers(List<Reactable> reactables) {
    List<Query.Filter> filters = new ArrayList<>();
    Set<Long> directorsIds =
        reactables.stream().map(Reactable::getDirectorId).collect(toSet());
    for (Long directorId : directorsIds) {
      filters.add(
          new Query.FilterPredicate(Entity.KEY_RESERVED_PROPERTY, Query.FilterOperator.EQUAL,
              KeyFactory.createKey(User.DATASTORE_KIND, directorId)));
    }
    Query query = new Query(User.DATASTORE_KIND)
        // Only select fields that can be shared with the public.
        .addProjection(new PropertyProjection(User.DATASTORE_FIRST_NAME, String.class))
        .addProjection(new PropertyProjection(User.DATASTORE_LAST_NAME, String.class));
    Util.setFilter(query, filters, Query.CompositeFilterOperator.OR);
    if (!filters.isEmpty()) {
      List<User> directors = DATASTORE_SERVICE.prepare(query)
          .asList(FetchOptions.Builder.withDefaults())
          .stream()
          .map(User::new)
          .collect(toList());
      Map<Long, List<Reactable>> reactableByDirectorId = reactables.stream().collect(groupingBy(
          Reactable::getDirectorId, toList()));
      for (User director : directors) {
        for (Reactable reactable : reactableByDirectorId.get(director.getId())) {
          reactable.setDirector(director);
        }
      }
    }
  }

  /**
   * Enriches {@link Reactable}s with data of {@link Reactable#director} first and last names.
   *
   * @param reactables to enrich
   * @param user       for which to enrich the reactables.
   */
  private static void enrichEvents(List<Reactable> reactables, User user) {
    List<Query.Filter> filters = new ArrayList<>();
    Set<Long> reactablesIds =
        reactables.stream().map(Reactable::getId).collect(toSet());
    for (Long reactableId : reactablesIds) {
      filters.add(
          new Query.FilterPredicate(ReactableEvent.DATASTORE_REACTABLE_ID,
              Query.FilterOperator.EQUAL,
              reactableId));
    }
    Query query = new Query(ReactableEvent.DATASTORE_KIND);
    Util.setFilter(query, filters, Query.CompositeFilterOperator.OR);
    if (!filters.isEmpty()) {
      List<ReactableEvent> allEvents = DATASTORE_SERVICE.prepare(query)
          .asList(FetchOptions.Builder.withDefaults())
          .stream()
          .map(ReactableEvent::new)
          .collect(toList());
      Map<Long, List<ReactableEvent>> eventByReactableId =
          allEvents.stream().collect(groupingBy(ReactableEvent::getReactableId, toList()));
      for (Reactable reactable : reactables) {
        boolean isUserDirector = user.getId() == reactable.getDirectorId();
        if (isUserDirector) {
          reactable.setViewed(true);
        }
        if (eventByReactableId.containsKey(reactable.getId())) {
          List<ReactableEvent> reactableEvents = eventByReactableId.get(reactable.getId());
          // Determine whether {@code reactable} was viewed by {@code user}.
          boolean viewed = reactable.isViewed();
          if (!viewed) {
            viewed = reactableEvents.stream()
                .anyMatch(reactableEvent -> reactableEvent.getUserId() == user.getId()
                    && reactableEvent.getEventType() == EventType.REACTABLE_VIEW);
          }
          reactable.setViewed(viewed);
          // Calculate reaction counters.
          Map<Emotion, Long> reactionCounters = reactableEvents.parallelStream()
              // Filter for reaction event not of the user.
              .filter(
                  reactableEvent -> reactableEvent.getEventType() == EventType.REACTABLE_REACTION
                      && reactableEvent.getUserId() != reactable.getDirectorId())
              // Group by reactions
              .collect(groupingBy(ReactableEvent::getReaction,
                  // Group by user IDs, to avoid duplicates
                  collectingAndThen(groupingBy(ReactableEvent::getUserId, counting()),
                      userIds -> (long) userIds.keySet().size())));
          reactable.setReactionCounters(reactionCounters);
          // Determine user reaction.
          if (!isUserDirector) {
            // Find a reaction event of user.
            Optional<ReactableEvent> reactionEvent = reactableEvents.stream()
                .filter(reactableEvent -> reactableEvent.getUserId() == user.getId()
                    && reactableEvent.getEventType() == EventType.REACTABLE_REACTION)
                .findAny();
            reactionEvent.ifPresent(
                reactableEvent -> reactable.setUserReaction(reactableEvent.getReaction()));
          }
        }
      }
    }
  }
}