package com.cxyaqcdm.fta.knowledge.repository;

import com.cxyaqcdm.fta.knowledge.entity.Event;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
@EnableNeo4jRepositories(basePackages = "com.cxyaqcdm.fta.knowledge.repository")
public interface EventRepository extends Neo4jRepository<Event, String> {
    
    @Query("MATCH (e:Event) WHERE e.name CONTAINS $name AND e.equipmentType = $equipmentType RETURN e")
    List<Event> findByEventNameAndEquipmentType(@Param("name") String name, @Param("equipmentType") String equipmentType);
    
    @Query("MATCH (e:Event) WHERE e.equipmentType = $equipmentType RETURN e")
    List<Event> findByEquipmentType(@Param("equipmentType") String equipmentType);
}
