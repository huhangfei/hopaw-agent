package com.agent.hopaw.avatar.model;

import java.util.List;

public class AvatarModelGroup {
    private String name;
    private List<String> models;

    public AvatarModelGroup() {
    }

    public AvatarModelGroup(String name, List<String> models) {
        this.name = name;
        this.models = models;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getModels() {
        return models;
    }

    public void setModels(List<String> models) {
        this.models = models;
    }
}
