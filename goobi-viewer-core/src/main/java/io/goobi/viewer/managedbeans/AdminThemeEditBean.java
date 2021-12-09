package io.goobi.viewer.managedbeans;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.faces.view.ViewScoped;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;

import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.model.viewer.themes.ThemeConfiguration;
import io.goobi.viewer.model.viewer.themes.ThemeLink;

@Named
@ViewScoped
public class AdminThemeEditBean implements Serializable {

    private static final long serialVersionUID = 7858762112113454834L;
    
    private ThemeConfiguration currentTheme = null;
    
    public void setCurrentThemeName(String themeName) throws DAOException {
        if(StringUtils.isNotBlank(themeName)) {
            ThemeConfiguration loadedTheme = DataManager.getInstance().getDao().getTheme(themeName);
            if(loadedTheme == null) {
                this.currentTheme = new ThemeConfiguration(themeName);
            } else {
                this.currentTheme = new ThemeConfiguration(loadedTheme);
            }
        }
    }
    
    public String getCurrentThemeName() {
        return Optional.ofNullable(currentTheme).map(ThemeConfiguration::getName).orElse("");
    }
    
    public ThemeConfiguration getCurrentTheme() {
        return currentTheme;
    }
    
    public void setCurrentTheme(ThemeConfiguration currentTheme) {
        this.currentTheme = currentTheme;
    }
    
    
    public List<ThemeLink.SocialMediaService> getSocialMediaServices() {
        return Arrays.asList(ThemeLink.SocialMediaService.values());
    }
    
    public List<ThemeLink.InternalService> getFooterServices() {
        return Arrays.asList(ThemeLink.InternalService.values());
    }
    
    public String saveTheme() throws DAOException {
        if(this.currentTheme != null && this.currentTheme.getId() != null) {            
            DataManager.getInstance().getDao().updateTheme(currentTheme);
        } else if(this.currentTheme != null) {
            DataManager.getInstance().getDao().addTheme(currentTheme);
        }
        return "pretty:adminThemes";
   }
}