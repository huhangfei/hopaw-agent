package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.SkillInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public interface ISkillService {
    Path getSkillDir();

    List<SkillInfo> listSkills();

    SkillInfo getSkill(String folderName);

    SkillInfo createSkill(SkillInfo skillInfo);

    SkillInfo updateSkill(String folderName, SkillInfo skillInfo);

    void deleteSkill(String folderName);

    SkillInfo readSkillFromDir(Path skillDir);
}
