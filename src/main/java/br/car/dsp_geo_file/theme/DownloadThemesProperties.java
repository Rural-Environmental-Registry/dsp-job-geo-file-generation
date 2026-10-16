package br.car.dsp_geo_file.theme;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "dsp.download")
public class DownloadThemesProperties {

    /** Same file the backend reads — the catalogue has one owner, {@code rer-dsp-core}. */
    private String themesFile = "file:/config/downloadThemesConfig.json";
}
