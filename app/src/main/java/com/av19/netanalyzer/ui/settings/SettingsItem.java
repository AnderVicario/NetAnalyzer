package com.av19.netanalyzer.ui.settings;

public class SettingsItem {
    private final int iconRes;
    private final String title;
    private final Type type;
    private final String settingKey; // Clave para SharedPreferences
    private String subtitle;
    private Boolean switchValue;
    private String textValue;
    private String[] options;
    private String[] optionValues;
    private int selectedOptionIndex;
    public SettingsItem(int iconRes, String title, String subtitle, Type type, Boolean switchValue, String settingKey) {
        this.iconRes = iconRes;
        this.title = title;
        this.subtitle = subtitle;
        this.type = type;
        this.switchValue = switchValue;
        this.settingKey = settingKey;
    }

    public SettingsItem(int iconRes, String title, String subtitle, Type type, String textValue, String settingKey) {
        this.iconRes = iconRes;
        this.title = title;
        this.subtitle = subtitle;
        this.type = type;
        this.textValue = textValue;
        this.settingKey = settingKey;
    }

    // Constructor para items con edit text

    // Constructor para items con opciones
    public SettingsItem(int iconRes, String title, String subtitle, Type type,
                        String[] options, String[] optionValues, String settingKey, int selectedOptionIndex) {
        this.iconRes = iconRes;
        this.title = title;
        this.subtitle = subtitle;
        this.type = type;
        this.options = options;
        this.optionValues = optionValues;
        this.settingKey = settingKey;
        this.selectedOptionIndex = selectedOptionIndex;
    }

    // Getters
    public int getIconRes() {
        return iconRes;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public Type getType() {
        return type;
    }

    public Boolean getSwitchValue() {
        return switchValue;
    }

    // Setters
    public void setSwitchValue(Boolean value) {
        this.switchValue = value;
    }

    public String[] getOptions() {
        return options;
    }

    public String[] getOptionValues() {
        return optionValues;
    }

    public int getSelectedOptionIndex() {
        return selectedOptionIndex;
    }

    public void setSelectedOptionIndex(int index) {
        this.selectedOptionIndex = index;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public enum Type {
        SWITCH, NAVIGATION, INFO, KEY
    }
}