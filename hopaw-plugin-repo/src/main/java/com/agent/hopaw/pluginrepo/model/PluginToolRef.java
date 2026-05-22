package com.agent.hopaw.pluginrepo.model;

public class PluginToolRef {

    private String name;
    private String description;
    private java.util.List<ParamRef> parameters;

    public PluginToolRef() {}

    public PluginToolRef(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public java.util.List<ParamRef> getParameters() { return parameters; }
    public void setParameters(java.util.List<ParamRef> parameters) { this.parameters = parameters; }

    public static class ParamRef {
        private String name;
        private String type;
        private String description;
        private boolean required;

        public ParamRef() {}

        public ParamRef(String name, String type, String description, boolean required) {
            this.name = name;
            this.type = type;
            this.description = description;
            this.required = required;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }
}