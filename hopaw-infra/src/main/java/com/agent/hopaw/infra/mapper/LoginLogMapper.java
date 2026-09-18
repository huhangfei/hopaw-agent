package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LoginLogMapper {

    int insert(LoginLog loginLog);

    List<LoginLog> findPage(@Param("userId") String userId,
                            @Param("ip") String ip,
                            @Param("result") String result,
                            @Param("offset") int offset,
                            @Param("size") int size);

    int countPage(@Param("userId") String userId,
                  @Param("ip") String ip,
                  @Param("result") String result);

    /** 查询指定用户最近N条登录记录 */
    List<LoginLog> findRecentByUser(@Param("userId") String userId, @Param("limit") int limit);

    /** 查询指定IP最近N条登录记录 */
    List<LoginLog> findRecentByIp(@Param("ip") String ip, @Param("limit") int limit);

    int deleteAll();
}
