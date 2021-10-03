package dev.jianmu.infrastructure.mapper.eventbrdige;

import dev.jianmu.eventbridge.aggregate.Target;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * @class: TargetMapper
 * @description: TargetMapper
 * @author: Ethan Liu
 * @create: 2021-08-16 09:21
 **/
public interface TargetMapper {
    @Select("SELECT * FROM `eb_target` WHERE id = #{id}")
    @Result(column = "destination_id", property = "destinationId")
    @Result(column = "bridge_id", property = "bridgeId")
    Optional<Target> findById(String id);

    @Select("SELECT * FROM `eb_target` WHERE bridge_id = #{bridgeId}")
    @Result(column = "destination_id", property = "destinationId")
    @Result(column = "bridge_id", property = "bridgeId")
    List<Target> findByBridgeId(String bridgeId);

    @Select("SELECT * FROM `eb_target` WHERE ref = #{ref}")
    @Result(column = "destination_id", property = "destinationId")
    @Result(column = "bridge_id", property = "bridgeId")
    Optional<Target> findByRef(String ref);

    @Select("SELECT * FROM `eb_target` WHERE destination_id = #{destinationId}")
    @Result(column = "destination_id", property = "destinationId")
    @Result(column = "bridge_id", property = "bridgeId")
    Optional<Target> findByDestinationId(String destinationId);

    @Insert("insert into eb_target(id, ref, bridge_id, name, type, destination_id) " +
            "values(#{id}, #{ref}, #{bridgeId}, #{name}, #{type}, #{destinationId})" +
            " ON DUPLICATE KEY UPDATE " +
            "name=#{name}, type=#{type}, destination_id=#{destinationId}")
    void saveOrUpdate(Target target);

    @Delete("DELETE FROM eb_target WHERE id = #{id}")
    void deleteById(String id);

    @Delete("DELETE FROM eb_target WHERE bridge_id = #{bridgeId}")
    void deleteByBridgeId(String bridgeId);
}
