package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.dto.TtsVoice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TtsVoiceMapper {

    /** 查询某 TTS 配置（渠道）的音色列表，按 id 升序 */
    List<TtsVoice> findByConfigId(@Param("configId") Long configId);

    /** 查询全部音色（备份用） */
    List<TtsVoice> findAll();

    /** 清空全部音色（导入恢复用） */
    int deleteAll();

    /** 统计某配置的音色数量 */
    int countByConfigId(@Param("configId") Long configId);

    /** 新增音色，返回影响行数（自增 id 回填到 voice.id） */
    int insert(TtsVoice voice);

    /** 更新音色 */
    int update(TtsVoice voice);

    /** 删除单条音色 */
    int deleteById(@Param("id") Long id);

    /** 删除某配置的全部音色（用于全量替换/重置） */
    int deleteByConfigId(@Param("configId") Long configId);
}
