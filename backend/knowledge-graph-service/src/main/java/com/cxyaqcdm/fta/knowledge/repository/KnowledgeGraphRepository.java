package com.cxyaqcdm.fta.knowledge.repository;

import com.cxyaqcdm.fta.knowledge.entity.UserEvent;
import com.cxyaqcdm.fta.knowledge.entity.GlobalEvent;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KnowledgeGraphRepository extends Neo4jRepository<UserEvent, String> {

    @Query("MATCH (e:UserEvent) WHERE e.userId = $userId RETURN e")
    List<UserEvent> findUserEventsByUserId(@Param("userId") String userId);

    @Query("MATCH (g:GlobalEvent) RETURN g")
    List<GlobalEvent> findAllGlobalEvents();

    @Query("MATCH (e:UserEvent) WHERE e.userId = $userId OR e.isGlobal = true RETURN e")
    List<UserEvent> findUserAndGlobalEvents(@Param("userId") String userId);

    @Query("MATCH (e1:UserEvent)-[r:CAUSES]->(e2:UserEvent) " +
           "WHERE e1.userId = $userId OR e1.isGlobal = true " +
           "RETURN e1, r, e2")
    List<Object> findUserEventRelationships(@Param("userId") String userId);

    @Query("MATCH (g1:GlobalEvent)-[r:CAUSES]->(g2:GlobalEvent) RETURN g1, r, g2")
    List<Object> findAllGlobalRelationships();

    @Query("MATCH (e1:UserEvent)-[r:CAUSES]->(e2:UserEvent) " +
           "WHERE (e1.userId = $userId OR e1.isGlobal = true) " +
           "RETURN e1, r, e2 " +
           "UNION ALL " +
           "MATCH (g1:GlobalEvent)-[r:CAUSES]->(g2:GlobalEvent) " +
           "RETURN g1 AS e1, r AS r, g2 AS e2")
    List<Object[]> findAllRelationshipsForUser(@Param("userId") String userId);

    @Query("MATCH (e:UserEvent) " +
           "WHERE (e.userId = $userId OR e.isGlobal = true) " +
           "AND ($equipmentType IS NULL OR e.equipmentType = $equipmentType) " +
           "RETURN e")
    List<UserEvent> findEventsByUserIdAndEquipmentType(
            @Param("userId") String userId,
            @Param("equipmentType") String equipmentType);

    @Query("MATCH (e:UserEvent) WHERE e.id = $eventId AND (e.userId = $userId OR e.isGlobal = true) RETURN e")
    UserEvent findEventByIdAndUserId(@Param("eventId") String eventId, @Param("userId") String userId);

    @Query("MATCH (e:UserEvent) WHERE (e.userId = $userId OR ($userId IS NULL)) AND e.docId = $docId RETURN e")
    List<UserEvent> findEventsByUserIdAndDocId(@Param("userId") String userId, @Param("docId") String docId);

    @Query("MATCH (e:UserEvent) WHERE (e.userId = $userId OR ($userId IS NULL)) AND e.docId = $docId DETACH DELETE e")
    void deleteEventsByUserIdAndDocId(@Param("userId") String userId, @Param("docId") String docId);

    @Query("MATCH (e:UserEvent) WHERE e.id = $eventId RETURN e")
    UserEvent findUserEventById(@Param("eventId") String eventId);

    @Query("MATCH (e1:UserEvent {id: $causeId})-[r:CAUSES]->(e2:UserEvent {id: $effectId}) RETURN count(r) > 0")
    boolean existsRelation(@Param("causeId") String causeId, @Param("effectId") String effectId);
}
